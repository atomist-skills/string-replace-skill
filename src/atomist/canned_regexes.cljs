(ns atomist.canned-regexes
  (:require ["url-regex" :as regex]
            [atomist.sdmprojectmodel :as sdm]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.api :as api]
            [atomist.json :as json]
            [goog.string :as gstring]
            [goog.string.format])
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
      (let [data (<! ((sdm/do-with-files
                       (fn [f]
                         (go
                           {(. ^js f -realPath) (into [] (re-seq url-regex (<! (sdm/get-content f))))}))
                       (:glob-pattern request)) (:project request)))
            file-matches (apply merge data)]
        (<! (api/simple-message request (gstring/format "found %s urls over %s files"
                                                        (reduce #(+ %1 (-> %2 vals first count)) 0 data)
                                                        (count data))))
        (<! (api/snippet-message request (json/->str file-matches) "application/json" "title"))
        (<! (handler request))))))
