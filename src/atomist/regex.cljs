(ns atomist.regex
  (:require [atomist.api :as api]
            [atomist.cljs-log :as log]
            [clojure.string :as s]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.test :refer [deftest is run-tests async]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn eval-captures [captures [_ exp]]
  (let [form (cljs.reader/read-string exp)]
    (cond
      (= 'inc (first form))
      (inc (js/parseInt (nth captures (second form))))
      (= 'dec (first form))
      (dec (js/parseInt (nth captures (second form))))
      :else
      exp)))

(defn eval-sed-extensions
  "\\L - turn the replacement to lower-case until a \\U or \\E
   \\l - turn the next character to lower case
   \\U - turn the replacement to upper-case until a \\L or \\E is found
   \\u - turn the next character to upper case
   \\E - Stop case conversion
   "
  [s]
  (apply str
         (loop [content (seq s)
                result []
                u false
                l false]
           (if (not (first content))
             result
             (cond
               (and
                (< 2 (count content))
                (= "\\" (first content))
                (= "u" (second content)))
               (recur (drop 3 content) (conj result (s/capitalize (nth content 2))) u l)
               (and
                (< 2 (count content))
                (= "\\" (first content))
                (= "U" (second content)))
               (recur (drop 3 content) (conj result (s/capitalize (nth content 2))) (not u) false)
               (and
                (< 2 (count content))
                (= "\\" (first content))
                (= "E" (second content)))
               (recur (drop 3 content) (conj result (nth content 2)) false false)
               :else
               (recur
                (rest content)
                (conj result (cond
                               u (s/upper-case (first content))
                               l (s/lower-case (first content))
                               :else (first content)))
                u
                l))))))

(defn- replacement-substitutions
  "captures are [matched, p1, p2, ..., offset, whole-string]"
  [match & captures]
  (-> match
      (s/replace "$$" "$")
      (s/replace "$&" (first captures))
      #_(s/replace "$`" "")                                 ;; TODO supporting this would require offset and string
      #_(s/replace "$'" "")                                 ;; TODO supporting this would require offset and string
      (s/replace #"\$(\d+?)" (fn [[_ s]]
                               (nth captures (js/parseInt s))))
      (s/replace #"\$\{(.*?)\}" (partial eval-captures captures))
      (eval-sed-extensions)))

(defn content-editor
  "compile an editor
     the editor uses a JavaScript RegEx to do a search and replace on the content in a File

     content editor is (File String) => String

     opts supported are g,i,m,u, and y"
  [search-regex replace opts]
  (fn [f content]
    (go
      (try
        (api/trace (gstring/format "content-editor %s" (.-path ^js f)))
        (if content
          (let [p (js/RegExp. search-regex (or opts ""))]
            {:new-content (.replace content p (partial replacement-substitutions replace))}))
        (catch :default ex
          (log/errorf "failed to run content-editor:  %s" ex))))))

(defn- is-test-expression [search replace opts c1 c2]
  (go
    (is (= {:new-content c2}
           (<! ((content-editor search replace opts)
                #js {:path "/anything"} c1))))))

(deftest subsitution-tests
  (async done
    (go
      (<! (is-test-expression
           "abc" "1234" "g"
           "go abc and abc then"
           "go 1234 and 1234 then"))
      (<! (is-test-expression
           "\"counter\": (\\d+)"
           "\"counter\": ${(inc 1)}"
           "g"
           "{\"counter\": 5 \"counter\": 7}"
           "{\"counter\": 6 \"counter\": 8}"))
      (<! (is-test-expression
           "a(\\d+)b(\\d+)" "A$1B$2" "g"
           "a1b2" "A1B2"))
      (<! (is-test-expression
           "([a-zA-Z]*?)_([a-zA-Z])" "$1\\u$2" "g"
           "camel_case and more of_the_same"
           "camelCase and more ofTheSame"))
      (<! (is-test-expression
           "([a-zA-Z]*?)_([a-zA-Z])" "$1\\U$2" "g"
           "camel_case and more of_the_same"
           "camelCase and more ofTheSame"))
      (<! (is-test-expression
           "([a-zA-Z]*?)_([a-zA-Z]+)" "$1\\U$2\\Estop" "g"
           "camel_case and more of_the_same"
           "camelCASEstop and more ofTHEstopSAMEstop"))
      (done))))
