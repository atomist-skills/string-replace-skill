(ns atomist.main
  (:require [atomist.json :as json]
            [atomist.api :as api]
            [atomist.promise :as promise]
            [stupendabot.k8-spec-updater]
            [stupendabot.make-some-pushes]
            [stupendabot.tokens]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! timeout chan]]
            [goog.crypt.base64 :as b64]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.editors :as editors]
            [atomist.sdmprojectmodel :as sdm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn editor
  "content - string to update"
  [content]
  )

(defn compile-simple-content-editor
  "use the sdm model "
  [request content-editor]
  (fn [f]
    (go
     (let [content (<! (sdm/get-content f))]
       (<! (sdm/set-content f (content-editor content))))
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
    (log/infof "update version of spec for %s" (-> request :linked-repos first))
    (go
     (<! (editors/perform-edits
          (compile-simple-content-editor request editor)
          (:glob-pattern request)
          (:token request)
          (:ref request)
          (gstring/format "Update %s/%s" (-> request :ref :owner) (-> request :ref :repo))))
     (<! (api/simple-message request (goog.string/format "edited %s/%s"
                                                            (-> request :ref :owner)
                                                            (-> request :ref :repo))))
     (handler request))))

(defn create-ref-from-first-linked-repo
  "construct a GitHub ref
    - the repo name is a parameter expression which can use parameter values
    - always use master branch"
  [handler]
  (fn [request]
    (handler (assoc request :ref {:repo (api/parameter-expression request (:repo request))
                                     :owner (:owner request)
                                     :branch (:branch request)}))))

(defn command-handler
  ""
  [request]
  (let [done-channel (chan)]
    ;; create a pipeline of handlers but always end by writing to the done channel and logging something
    ((-> (fn [ch-request]
           (log/info "----> finished - string-replace skill")
           (go (>! (:done-channel ch-request) :done)))
         (run-editors)
         (create-ref-from-first-linked-repo)
         (api/extract-linked-repos)
         (api/extract-github-user-token)
         (api/check-required-parameters
          {:name "glob-pattern"
           :required true
           :pattern ".*"
           :validInput "**/*.md"}
          {:name "expression"
           :required true
           :pattern ".*"
           :validInput "s/<match>/<replace>/g"})
         (api/set-message-id)) (assoc request
                                 :done-channel done-channel
                                 :branch "master"))
    done-channel))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (promise/chan->promise
   (command-handler (assoc (js->clj data :keywordize-keys true)
                      :sendreponse sendreponse))))
