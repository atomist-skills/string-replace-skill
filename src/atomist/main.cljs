(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [clojure.string :as s]
            [goog.crypt.base64 :as b64]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.editors :as editors]
            [atomist.sdmprojectmodel :as sdm]
            [atomist.json :as json]
            [atomist.api :as api]
            [atomist.promise :as promise]
            [atomist.github :as github]
            [cljs-node-io.proc :as proc]
            [cljs-node-io.core :as io]
            ["url-regex" :as regex])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def url-regex (regex))

(defn run-regular-expressions
  "middleware
     clone and run regexes against the glob-pattern in the request

     - expects that a Repo ref is already present in the request
     - expects that this originated from a Command Handler request

     Send two Chat messages with the data we've found."
  [handler]
  (fn [request]
    (go
     (let [data
           (<! (sdm/do-with-shallow-cloned-project
                (sdm/do-with-files
                 (fn [f]
                   (go
                    {(. ^js f -realPath) (into [] (re-seq url-regex (<! (sdm/get-content f))))}))
                 (:glob-pattern request))
                (:token request)
                (:ref request)))
           file-matches (apply merge data)]
       (<! (api/simple-message request (gstring/format "found %s urls over %s files"
                                                       (reduce #(+ %1 (-> %2 vals first count)) 0 data)
                                                       (count data))))
       (<! (api/snippet-message request (json/->str file-matches) "application/json" "title"))
       (handler request)))))

(defn content-editor
  "compile an editor
     the editor uses a JavaScript RegEx to do a search and replace on the content in a File

     content editor is (File String) => String"
  [search-regex replace opts]
  (fn [f content]
    (go
     (let [p (re-pattern search-regex)]
       (if (s/starts-with? opts "g")
         {:new-content (s/replace-all content p replace)}
         {:new-content (s/replace content p replace)})))))

(defn file-stream-editor
  "compile an editor
    the editor uses sed to process a File

    This only accepts s commands for sed
    The only s options supported is g
    The editor can be compiled in either basic or extended mode, and this impacts how sed is executed

    stream editor is (File String) => String"
  [s & {:keys [type]}]
  "content - string to update"
  (if (re-find #"s/(.*)/(.*)/g?" s)
    (fn [f content]
      (let [path (. ^js f -realPath)]
        (log/debugf "spawn sed on %s for file %s" s path)
        (go
         (let [[error stdout stderr] (<! (proc/aexec (gstring/format "sed %s '%s' %s"
                                                                     (if (= type "extended") "-E" "")
                                                                     s
                                                                     path)))]
           (if error
             (do
               (log/errorf "stderr:  %s" stderr)
               {:error stderr})
             {:new-content (if (string? stdout)
                             stdout
                             (io/slurp stdout))})))))))

(defn compile-simple-content-editor
  "compile a file editor
    params
      request
      content-editor (File String) => String"
  [request editor]
  (fn [f]
    (go
     (let [content (<! (sdm/get-content f))
           {:keys [error new-content]} (<! (editor f content))]
       (cond
         (= content new-content)
         (log/debug "content not changed")
         (and new-content (not error))
         (<! (sdm/set-content f new-content))
         :else
         (log/errorf "error running stream editor: %s" error)))
     true)))

(defn config->branch-name [config-name branch-name]
  (gstring/format "%s-on-%s"
                  (-> config-name (s/replace-all #"\s" ""))
                  branch-name))

(defn run-editors
  "middleware
   run a conditional json editor on all files matching a glob
   relies on the ch-request containing
     - :ref with GitHubRef of the repo to operate on - create-ref middleware
     - :token for Github - api/extract-github-user-token middleware
     - :glob-pattern
     - :path-to-spec
     - :image selected for replacement - select-recent-image middleware

     TODO - branch naming guidelines should be target branch specific"
  [handler]
  (fn [request]
    (let [branch (config->branch-name (-> request :configuration :name) (-> request :ref :branch))]
      (cond
        (and (:expression request) (:glob-pattern request))
        (if-let [editor (cond
                          (#{"basic" "extended"} (:parserType request))
                          (file-stream-editor (:expression request) :type (:parserType request))
                          (= "perl" (:parserType request))
                          (let [[_ search replace opts] (re-find #"s/(.*)/(.*)/(g?)" (:expression request))]
                            (content-editor search replace opts))
                          :else
                          (let [[_ search replace opts] (re-find #"s/(.*)/(.*)/(g?)" (:expression request))]
                            (content-editor search replace opts))
                          #_(file-stream-editor (:expression request)))]
          (go
           (let [edit-result (<! (editors/perform-edits-in-PR-with-multiple-glob-patterns
                                  (compile-simple-content-editor request editor)
                                  (s/split (:glob-pattern request) #",")
                                  (:token request)
                                  (:ref request)
                                  {:target-branch "master"
                                   :branch branch
                                   :title (-> request :configuration :name)
                                   :body (gstring/format "Ran string replacement `%s` on %s\n[atomist:edited]"
                                                         (:expression request)
                                                         (->> (s/split (:glob-pattern request) ",")
                                                              (map #(gstring/format "`%s`" %))
                                                              (interpose ",")
                                                              (apply str)))}))
                 pullRequest (<! (github/pr-channel request branch))]
             (handler (merge request
                             {:edit-result edit-result}
                             (if-let [n (:number pullRequest)]
                               {:pull-request-number n})))))
          (api/finish request :failure (gstring/format "this skill will only run expressions of the kind s/.*/.*/g?" (:expression request))))
        :else
        (do
          (log/warn "run-editors requires both a glob-pattern and an expression")
          (api/finish request :failure "configuration did not contain `expression` and `glob-pattern`"))))))

(defn log-attempt [handler]
  (fn [request]
    (log/infof "Push Request %s over %s on %s" (:expression request) (:glob-pattern request) (:ref request))
    (handler request)))

(defn add-default-glob-pattern [handler]
  (fn [request]
    (if (not (:glob-pattern request))
      (handler (assoc request :glob-pattern "**/*"))
      (handler request))))

(defn pr-link [request]
  (gstring/format "https://github.com/%s/%s/pull/%s" (-> request :ref :owner) (-> request :ref :repo) (or (-> request :pull-request-number) "")))

(defn send-status [request]
  (if (and (:pull-request-number request) (= :raised (:edit-result request)))
    (gstring/format "**StringReplaceSkill** completed successfully:  [PR raised](%s)" (pr-link request))
    "completed without raising PullRequest"))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   (fn [request]
     (cond

       ;; Invoked by Command Handler (report on certain regexes in the codebase)
       (= "FindUrlSkill" (:command request))
       ((-> (api/finished :message "successfully ran FindUrlSkill")
            (run-regular-expressions)
            (api/create-ref-from-first-linked-repo)
            (api/extract-linked-repos)
            (api/extract-github-user-token)
            (api/extract-cli-parameters [[nil "--url" nil]])
            (api/set-message-id)) request)

       ;; Invoked by Command Handler (test out the regex from slack)
       (= "StringReplaceSkill" (:command request))
       ((-> (api/finished :message "CommandHandler"
                          :send-status send-status)
            (run-editors)
            (api/add-skill-config-by-configuration-parameter :configuration :glob-pattern :expression :scope)
            (api/create-ref-from-first-linked-repo)
            (api/extract-linked-repos)
            (api/extract-github-user-token)
            (api/check-required-parameters {:name "configuration"
                                            :required true
                                            :pattern ".*"
                                            :validInput "must be a valid configuration name"})
            (api/extract-cli-parameters [[nil "--configuration PATTERN" "existing configuration"]])
            (api/set-message-id)) (assoc request :branch "master"))

       ;; Push Event (try out config parameters)
       (contains? (:data request) :Push)
       ((-> (api/finished :message "Push event"
                          :send-status send-status)
            (run-editors)
            (log-attempt)
            (api/extract-github-token)
            (api/create-ref-from-push-event)
            (add-default-glob-pattern)
            (api/add-skill-config :glob-pattern :expression :schedule :scope :parserType)
            (api/skip-push-if-atomist-edited)) request)

       :else
       (go
        (log/errorf "Unrecognized event %s" request)
        (api/finish request))))))
