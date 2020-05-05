(ns user
  (:require [atomist.main]
            [atomist.cljs-log :as log]))

(enable-console-print!)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_STAGING))
(def github-token (.. js/process -env -GITHUB_TOKEN))

(defn fake-handler [& args]
  (log/info "args " args))

(comment
 ;; EVENT
 ;; - needs both API_KEY and github token in scmProvider credential
 ;; - subsequent Pushes of this should run but do nothing.  They might re-run if the config is updated, but this would be confusing
  (.catch
   (.then
    (atomist.main/handler #js {:data {:Push [{:branch "master"
                                              :repo {:name "elephants"
                                                     :org {:owner "atomisthqa"
                                                           :scmProvider {:providerId "zjlmxjzwhurspem"
                                                                         :credential {:secret github-token}}}}
                                              :after {:message ""}}]}
                               :secrets [{:uri "atomist://api-key" :value token}]
                               :configuration {:name "whales->elephants"
                                               :parameters [{:name "expression" :value "s/whales/elephants/g"}
                                                            {:name "glob-pattern" :value ["README.md"]}]}
                               :extensions [:team_id "AK748NQC5"]}
                          fake-handler)
    (fn [v] (log/info "value " v)))
   (fn [error] (log/info "error " error)))

 ;; what if the branch whales->elephants-on-master is changed, these should still run because it might have been rebased
  (.catch
   (.then
    (atomist.main/handler #js {:data {:Push [{:branch "whales->elephants-on-master"
                                              :repo {:name "elephants"
                                                     :org {:owner "atomisthqa"
                                                           :scmProvider {:providerId "zjlmxjzwhurspem"
                                                                         :credential {:secret github-token}}}}
                                              :after {:message "[:atomist:edited]"}}]}
                               :secrets [{:uri "atomist://api-key" :value token}]
                               :configuration {:name "whales->elephants"
                                               :parameters [{:name "expression" :value "s/whales/elephants/g"}
                                                            {:name "glob-pattern" :value ["README.md"]}]}
                               :extensions [:team_id "AK748NQC5"]}
                          fake-handler)
    (fn [v] (log/info "value " v)))
   (fn [error] (log/info "error " error)))

 ;; COMMAND HANDLER
 ;;   - needs only API_KEY
 ;;   - extracts repo from channel
 ;;   - extracts github token from ResourceUser
  (.catch
   (.then
    (atomist.main/handler #js {:command "StringReplaceSkill"
                               :source {:slack {:channel {:id "CTGGW07B6"}
                                                :user {:id "UDF0NFB5M"}}}
                               :team {:id "AK748NQC5"}
                               :configurations [{:name "elephants"
                                                 :enabled true
                                                 :parameters [{:name "glob-pattern" :value "README.md,**/*.html"}
                                                              {:name "expression" :value "s/whales/elephants/g"}]}]
                               :raw_message "sed --configuration=elephants"
                               :secrets [{:uri "atomist://api-key" :value token}]}
                          fake-handler)
    (fn [v] (log/info "value " v)))
   (fn [error] (log/error "error " error)))

 ;; COMMAND HANDLER
 ;;
  (.catch
   (.then
    (atomist.main/handler #js {:command "FindUrlSkill"
                               :source {:slack {:channel {:id "CTGGW07B6"}
                                                :user {:id "UDF0NFB5M"}}}
                               :api_version "1"
                               :correlation_id "corrid"
                               :team {:id "AK748NQC5"}
                               :raw_message "find-by-regex --url --glob-pattern=**/*"
                               :secrets [{:uri "atomist://api-key" :value token}]}
                          fake-handler)
    (fn [v] (log/info "value " v)))
   (fn [error] (log/error "error " error))))
