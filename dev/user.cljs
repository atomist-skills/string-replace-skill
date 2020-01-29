(ns user
  (:require [atomist.main]
            [atomist.cljs-log :as log]))

(enable-console-print!)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_PROD))
(def github-token (.. js/process -env -GITHUB_TOKEN))

(defn fake-handler [& args]
  (log/info "args " args))

(comment
 ;; EVENT
 (.catch
  (.then
   (atomist.main/handler #js {:data {:Push [{:branch "master"
                                             :repo {:name "string-replace-skill"
                                                    :org {:owner "atomist-skills"
                                                          :scmProvider {:providerId "zjlmxjzwhurspem"
                                                                        :credential {:secret github-token}}}}
                                             :after {:message ""}}]}
                              :secrets [{:uri "atomist://api-key" :value token}]
                              :extensions [:team_id "T095SFFBK"]}
                         fake-handler)
   (fn [v] (log/info "value " v)))
  (fn [error] (log/error "error " error)))

 ;; COMMAND HANDLER
 (.catch
  (.then
   (atomist.main/handler #js {:command "StringReplaceSkill"
                              :source {:slack {:channel {:id "C19ALS7P1"}
                                               :user {:id "U09MZ63EW"}}}
                              :team {:id "T095SFFBK"}
                              :parameters [{:name "expression" :value "s/data streams/whole elephants/g"}]
                              :raw_message "string-replace-skill --glob-pattern=README.md"
                              :secrets [{:uri "atomist://api-key" :value token}]}
                         fake-handler)
   (fn [v] (log/info "value " v)))
  (fn [error] (log/error "error " error))))