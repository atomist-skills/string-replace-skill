(ns user
  (:require [atomist.main]
            [atomist.cljs-log :as log]))

(enable-console-print!)

(def token )

(defn fake-handler [& args]
  (log/info "args " args))

(comment
 (.catch
  (.then
   (atomist.main/handler #js {:command "StringReplaceSkill"
                              :source {:slack {:channel {:id "C19ALS7P1"}
                                               :user {:id "U09MZ63EW"}}}
                              :team {:id "T095SFFBK"}
                              :parameters []
                              :secrets [{:uri "atomist://api-key" :value token}]}
                         fake-handler)
   (fn [v] (log/info "value " v)))
  (fn [error] (log/error "error " error))))