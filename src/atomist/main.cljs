;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [clojure.string :as s]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.editors :as editors]
            [atomist.api :as api]
            [atomist.github :as github]
            [atomist.repo-filter :as repo-filter]
            [atomist.canned-regexes :as regexes]
            [atomist.sed :as sed]
            [atomist.regex :as regex]
            [cljs-node-io.core :as io])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn compile-simple-content-editor
  "compile a file editor
    params
      request
      content-editor (File String) => String"
  [request]
  (fn [f]
    (go
      (api/trace "compile-simple-content-editor")
      (try
        (let [content (io/slurp f)
              {:keys [error new-content]} (<! ((:editor request) f content))]
          (cond
            (= content new-content)
            (log/debug "content not changed")

            (and new-content (not error))
            (io/spit f new-content)

            :else
            (log/errorf "error running stream editor: %s" error)))
        (catch :default ex
          (log/error ex "failed to run editor")))
      true)))

(defn config->branch-name [config-name branch-name]
  (gstring/format "%s-on-%s"
                  (-> config-name (s/replace-all #"\s" ""))
                  branch-name))

(defn check-config
  "prepare editor based on configuration"
  [handler]
  (fn [request]
    (go
      (api/trace "check-config")
      (if (and (:expression request) (:glob-pattern request) (not (empty? (:glob-pattern request))))
        (if-let [editor (cond
                          (#{"basic" "extended"} (:parserType request))
                          (sed/file-stream-editor (:expression request) :type (:parserType request))
                          (= "perl" (:parserType request))
                          (let [[_ search replace opts] (re-find #"s/(.*)/(.*)/([gim]?)" (:expression request))]
                            (regex/content-editor search replace opts))
                          :else
                          (let [[_ search replace opts] (re-find #"s/(.*)/(.*)/([gim]?)" (:expression request))]
                            (regex/content-editor search replace opts))
                          #_(file-stream-editor (:expression request)))]
          (<! (handler (assoc request
                              :editor editor
                              :pr-config {:target-branch "master"
                                          :branch (-> request :configuration :name (config->branch-name (or (:branch request) (-> request :ref :branch))))
                                          :title (-> request :configuration :name)
                                          :body (gstring/format "Ran string replacement `%s` on %s\n[atomist:edited]"
                                                                (:expression request)
                                                                (->> (:glob-pattern request)
                                                                     (map #(gstring/format "`%s`" %))
                                                                     (interpose ",")
                                                                     (apply str)))})))
          (<! (api/finish request :failure (gstring/format "this skill will only run expressions of the kind s/.*/.*/g?" (:expression request)))))
        (do
          (log/warn "run-editors requires both a glob-pattern and an expression")
          (<! (api/finish request :failure "configuration did not contain `expression` and `glob-pattern`")))))))

(defn check-for-new-pull-request
  "set up branch-name and watch for new pull requests if they occur"
  [handler]
  (fn [request]
    (go
      (api/trace "check-for-new-pull-request(enter)")
      (let [response (<! (handler request))]
        (api/trace "check-for-new-pull-request(exit)")
        (let [pullRequest (<! (github/pr-channel request (-> request :pr-config :branch)))]
          (assoc response :pull-request-number (:number pullRequest)))))))

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
    (go
      (api/trace "run-editors")
      (<! ((-> (compile-simple-content-editor request)
               (editors/do-with-glob-patterns (:glob-pattern request))
               (editors/check-do-with-glob-patterns-errors)) (:project request)))
      (<! (handler request)))))

(defn log-attempt [handler]
  (fn [request]
    (go
      (log/infof "Push Request %s over %s on %s" (:expression request) (:glob-pattern request) (:ref request))
      (<! (handler request)))))

(defn add-default-glob-pattern [handler]
  (fn [request]
    (go
      (if (or
           (not (:glob-pattern request))
           (empty? (:glob-pattern request)))
        (<! (handler (assoc request :glob-pattern ["**/*"])))
        (<! (handler request))))))

(defn pr-link [request]
  (gstring/format "https://github.com/%s/%s/pull/%s" (-> request :ref :owner) (-> request :ref :repo) (or (-> request :pull-request-number) "")))

(defn send-status [request]
  (if (and (:pull-request-number request) (= :raised (:edit-result request)))
    (gstring/format "**StringReplaceSkill** completed successfully:  [PR raised](%s)" (pr-link request))
    "completed without raising PullRequest"))

(defn skip-if-not-master [handler]
  (fn [request]
    (go
      (if (or (= "master" (or (:branch request)
                              (-> request :ref :branch)))
              (= "pr" (:update request)))
        (<! (handler request))
        (<! (api/finish :success "skipping" :visibility :hidden))))))

(defn skip-if-configuration-has-schedule [handler]
  (fn [request]
    (go
      (api/trace "skip-if-configuration-has-schedule")
      (if (nil? (:schedule request))
        (<! (handler request))
        (<! (api/finish request :success "skip Pushes with schedules" :visibility :hidden))))))

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
            (regexes/run-regular-expressions)
            (api/clone-ref)
            (api/create-ref-from-first-linked-repo)
            (api/extract-linked-repos)
            (api/extract-github-user-token)
            (api/from (fn [request] (log/info "globs " (:glob-pattern request)) :a) :key :whatever)
            (api/from (fn [request] (conj [] (:glob-pattern request))) :key :glob-pattern)
            (api/extract-cli-parameters [[nil "--url" nil]
                                         [nil "--glob-pattern PATTERN" "glob pattern"]])
            (api/set-message-id)
            (api/status))
        (assoc request :branch "master"))

       ;; Invoked by Command Handler (test out the regex from slack)
       (= "StringReplaceSkill" (:command request))
       ((-> (api/finished :message "CommandHandler"
                          :send-status send-status)
            (run-editors)
            (api/edit-inside-PR :pr-config)
            (api/clone-ref)
            (check-for-new-pull-request)
            (check-config)
            (log-attempt)
            (api/add-skill-config-by-configuration-parameter :configuration :glob-pattern :expression :scope :update)
            (api/create-ref-from-first-linked-repo)
            (api/extract-linked-repos)
            (api/extract-github-user-token)
            (api/check-required-parameters {:name "configuration"
                                            :required true
                                            :pattern ".*"
                                            :validInput "must be a valid configuration name"})
            (api/extract-cli-parameters [[nil "--configuration PATTERN" "existing configuration"]
                                         [nil "--commit-on-master"]])
            (api/set-message-id)
            (api/status :send-status (fn [request]
                                       (if (:pull-request-number request)
                                         (gstring/format "**StringReplaceSkill** CommandHandler completed successfully:  [PR raised](%s)" (pr-link request))
                                         "CommandHandler completed without raising PullRequest"))))
        (assoc request :branch "master"))

       ;; Push Event (try out config parameters)
       (contains? (:data request) :Push)
       ((-> (api/finished :message "Push event"
                          :send-status send-status)
            (run-editors)
            (api/edit-inside-PR :pr-config)
            (api/clone-ref)
            (check-for-new-pull-request)
            (check-config)
            (log-attempt)
            (api/extract-github-token)
            (skip-if-not-master)
            (api/create-ref-from-push-event)
            (add-default-glob-pattern)
            (skip-if-configuration-has-schedule)
            (api/add-skill-config)
            (api/skip-push-if-atomist-edited)
            (api/log-event)
            (api/status :send-status (fn [request]
                                       (if (:pull-request-number request)
                                         (gstring/format
                                          "**StringReplaceSkill** handled Push Event:  [PR raised](%s)"
                                          (pr-link request))
                                         "Push event handler completed without raising PullRequest"))))
        request)

       (contains? (:data request) :OnSchedule)
       ((-> (api/finished :message "String-Replace scheduled")
            (api/from (fn [{:keys [plan]}]
                        (let [details
                              (->> plan
                                   (map #(select-keys % [:pull-request-number :edit-result :ref]))
                                   (into []))]
                          (cljs.pprint/pprint details)
                          details)) :key :batch-details)
            (api/repo-iterator (fn [request ref] (repo-filter/ref-matches-repo-filter? request ref (:scope request)))
                               (-> (api/finished)
                                   (run-editors)
                                   (api/edit-inside-PR :pr-config)
                                   (api/clone-ref)
                                   (check-for-new-pull-request)))
            (check-config)
            (add-default-glob-pattern)
            (api/add-skill-config :glob-pattern :expression :scope :parserType)
            (api/status :send-status (fn [request]
                                       (let [c (->> (:plan request)
                                                    (filter :pull-request-number)
                                                    (count))]
                                         (gstring/format "%d open Pull Requests" c)))))
        (assoc request :branch "master"))

       :else
       (go
         (log/errorf "Unrecognized event %s" request)
         (<! (api/finish request)))))))
