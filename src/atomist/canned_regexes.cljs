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

(ns atomist.canned-regexes
  (:require ["url-regex" :as regex]
            [atomist.gitflows :as gitflow]
            [cljs.core.async :refer [<! >! timeout chan]]
            [atomist.api :as api]
            [atomist.json :as json]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs-node-io.core :as io])
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
      (let [data (<! ((gitflow/do-with-files
                       (fn [f]
                         (go
                           {(.getPath f) (into [] (re-seq url-regex (io/slurp f)))}))
                       (:glob-pattern request))
                      (:project request)))
            file-matches (apply merge data)]
        (<! (api/simple-message request (gstring/format "found %s urls over %s files"
                                                        (reduce #(+ %1 (-> %2 vals first count)) 0 data)
                                                        (count data))))
        (<! (api/snippet-message request (json/->str file-matches) "application/json" "title"))
        (<! (handler request))))))
