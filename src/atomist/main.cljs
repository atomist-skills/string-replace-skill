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
            [atomist.promise :as promise])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn editor [s]
  "content - string to update"
  (let [[_ pattern replace] (re-find #"s/(.*)/(.*)/g" s)]
    (fn [content]
      (s/replace-all content (re-pattern pattern) replace))))

(defn compile-simple-content-editor
  "use the sdm model"
  [request content-editor]
  (fn [f]
    (go
     (let [content (<! (sdm/get-content f))
           new-content (content-editor content)]
       (if (= content new-content)
         (log/info "content not changed - "))
       (<! (sdm/set-content f new-content)))
     true)))

(defn run-editors
  "run a conditional json editor on all files matching a glob
   relies on the ch-request containing
     - :ref with GitHubRef of the repo to operate on - create-ref middleware
     - :token for Github - api/extract-github-user-token middleware
     - :glob-pattern
     - :path-to-spec
     - :image selected for replacement - select-recent-image middleware"
  [handler]
  (fn [request]
    (log/infof "run editor %s over %s on %s" (:expression request) (:glob-pattern request) (-> request :ref))
    (cond
      (and (:expression request) (:glob-pattern request))
      (go
       (<! (editors/perform-edits-in-PR-with-multiple-glob-patterns
            (compile-simple-content-editor request (editor (:expression request)))
            (s/split (:glob-pattern request) #",")
            (:token request)
            (:ref request)
            {:target-branch "master"
             :branch (str (random-uuid))
             :title "String Replace Skill running"
             :body (gstring/format "Update %s/%s - running %s over %s\n[atomist:edited]"
                                   (-> request :ref :owner)
                                   (-> request :ref :repo)
                                   (:expression request)
                                   (:glob-pattern request))}))
       (<! (api/simple-message request (goog.string/format "edited %s/%s"
                                                           (-> request :ref :owner)
                                                           (-> request :ref :repo))))
       (handler request))
      :else
      (do
        (log/warn "run-editors requires both a glob-pattern and an expression")
        (api/finish request :failure "configuration did not contain `expression` and `glob-pattern`")))))

(defn log-attempt [handler]
  (fn [request]
    (log/infof "Push Request %s over %s on %s" (:expression request) (:glob-pattern request) (:ref request))
    (handler request)))

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

       ;; Invoked by Command Handler (test out the regex from slack)
       (= "StringReplaceSkill" (:command request))
       ((-> (api/finished :message "CommandHandler" :success "StringReplaceSkill CommandHandler completed successfully")
            (run-editors)
            (api/create-ref-from-first-linked-repo)
            (api/extract-linked-repos)
            (api/extract-github-user-token)
            (api/check-required-parameters {:name "glob-pattern"
                                            :required true
                                            :pattern ".*"
                                            :validInput "**/*.md"}
                                           {:name "expression"
                                            :required true
                                            :pattern ".*"
                                            :validInput "s/<match>/<replace>/g"})
            (api/extract-cli-parameters [[nil "--glob-pattern PATTERN" "glob pattern"]
                                         [nil "--expression REGEX" "REGEX expression"]])
            (api/set-message-id)) (assoc request :branch "master"))

       ;; Push Event (try out config parameters)
       (contains? (:data request) :Push)
       ((-> (api/finished :message "Push event" :success "StringReplaceSkill handled Push event successfully")
            (run-editors)
            (log-attempt)
            (api/extract-github-token)
            (api/create-ref-from-push-event)
            (api/add-skill-config :glob-pattern :expression :schedule :scope)
            (api/skip-push-if-atomist-edited)) request)

       :else
       (go
        (log/errorf "Unrecognized event %s" request)
        (api/finish request))))))
