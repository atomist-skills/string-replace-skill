(ns user
  (:require [atomist.main]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.local-runner :refer [fake-push fake-schedule call-event-handler fake-command-handler set-env]]
            [http.client :as client]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)
(set-env :prod-github-auth)

(comment

 (-> (fake-schedule "AEIB5886C")
     (assoc :configuration {:name "counter"
                            :parameters [{:name "expression" :value "s/(counter: )([0-9]+)/$1${(inc 2)}/g"}
                                         {:name "glob-pattern" :value ["README.md"]}
                                         #_{:name "scope" :value {:includes [{:ownerId "AEIB5886C_slimslender_AEIB5886C"
                                                                              :repoIds ["AEIB5886C_AEIB5886C_slimslender_241726138"]}]}}
                                         {:name "scope" :value {}}
                                         {:name "schedule" :value {:cronExpression "0 */6 * * *"}}
                                         {:name "update" :value "pr_default"}]})
     (call-event-handler atomist.main/handler))

 (-> (fake-push "AEIB5886C" "slimslender" "elephants" "master")
     (assoc :configuration {:name "Camel Case Converter"
                            :parameters [{:name "expression" :value "s/([a-zA-Z]*?)_([a-zA-Z])/$1\\U$2/g"}
                                         {:name "glob-pattern" :value ["*.{yml,yaml}"]}
                                         {:name "scope" :value {:includes nil :excludes nil}}]})
     (call-event-handler atomist.main/handler))

 ;; EVENT
 ;; - needs both API_KEY and github token in scmProvider credential
 ;; - subsequent Pushes of this should run but do nothing.  They might re-run if the config is updated, but this would be confusing
 (-> (fake-push "T29E48P34" "atomist" "string-replace-tests" "master")
     (assoc :configuration {:name "Snake case to camel case for YAML"
                            :parameters [{:name "expression" :value "s/([a-zA-Z]*?)_([a-zA-Z])/$1\\U$2/g"}
                                         {:name "glob-pattern" :value ["*.{yml,yaml}" "**/*.html"]}]})
     (call-event-handler atomist.main/handler))

 ;; complain about missing expression and glob-pattern
 (-> (fake-push "T29E48P34" "atomist" "string-replace-tests" "master")
     (call-event-handler atomist.main/handler))

 ;; complain about Pushes with schedules
 (-> (fake-push "T29E48P34" "atomist" "string-replace-tests" "master")
     (assoc :configuration {:name "whatever"
                            :parameters [{:name "schedule" :value {}}]})
     (call-event-handler atomist.main/handler))

 ;; STAGING what if the branch whales->elephants-on-master is changed, these should still run because it might have been rebased
 (-> (fake-push "AK748NQC5" "atomisthqa" "elephants" "whales->elephants-on-master")
     (assoc :configuration {:name "whales->elephants"
                            :parameters [{:name "expression" :value "s/whales/elephants/g"}
                                         {:name "glob-pattern" :value ["README.md"]}]})
     (call-event-handler atomist.main/handler))

 ;; STAGING COMMAND HANDLER
 ;;   - needs only API_KEY
 ;;   - extracts repo from channel
 ;;   - extracts github token from ResourceUser
 (-> (fake-command-handler "AK748NQC5" "StringReplaceSkill" "sed --configuration=elephants" "CTGGW07B6" "UDF0NFB5M")
     (assoc :configurations [{:name "elephants"
                              :enabled true
                              :parameters [{:name "glob-pattern" :value "README.md,**/*.html"}
                                           {:name "expression" :value "s/whales/elephants/g"}]}])
     (call-event-handler atomist.main/handler))

 ;; STAGING COMMAND HANDLER
 ;;
 (-> (fake-command-handler "AK748NQC5" "FindUrlSkill" "find-by-regex --url --glob-pattern=**/*" "CTGGW07B6" "UDF0NFB5M")
     (call-event-handler)))
