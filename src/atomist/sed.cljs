(ns atomist.sed
  (:require [cljs-node-io.core :as io]
            [atomist.cljs-log :as log]
            [cljs.core.async :refer [<! >! timeout chan]]
            [cljs-node-io.proc :as proc]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
      (let [path (.getPath f)]
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
