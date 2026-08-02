(ns d0.d0-spec-analyzer
  (:require [clojure.string :refer [join split-lines]]
            [edamame.core :refer [parse-string-all]]
            [y0.builtins :refer [add-builtins]]
            [y0.config :refer [*y0-path* cwd language-map-from-config]]
            [y0.polyglot-loader :refer [eval-mstore load-with-deps]]
            [y0.rules :refer [*skip-recoverable-assertions* apply-statements]]
            [y0.spec-analyzer :refer [process-lines]]
            [y0.status :refer [ok let-s unwrap-status]]))

(defn- start-block [v]
  (assoc v :current-block []))

(defn- add-line [v line]
  (update v :current-block (fnil conj []) line))

(defn- end-block [v key]
  (-> v
      (assoc key (join "\n" (:current-block v)))
      (dissoc :current-block)))

(defn- fire [v]
  (let [clj-code (join "\n" (:current-block v))]
    ((:callback v) (:d0-code v) (:c0-code v) clj-code)
    (dissoc v :current-block :d0-code :c0-code)))

;; The state machine walks through the three blocks of a translation example
;; using a dedicated state per block, and an "after" state between blocks that
;; enforces that the blocks are strictly consecutive (no line in between).
(def d0-spec-sm
  {;; Outside any example. Look for the opening `wisp` fence.
   :init [{:pattern #"```wisp\s*"
           :transition :in-d0
           :update-fn (fn [v _m] (start-block v))}
          {:pattern #"```.*"
           :transition :skip}]
   ;; Collecting the d0 code (`wisp` block).
   :in-d0 [{:pattern #"```\s*"
            :transition :after-d0
            :update-fn (fn [v _m] (end-block v :d0-code))}
           {:update-fn (fn [v [line]] (add-line v line))}]
   ;; The line immediately after the d0 block must open the `go` block.
   :after-d0 [{:pattern #"```go\s*"
               :transition :in-c0
               :update-fn (fn [v _m] (start-block v))}
              ;; A new `wisp` fence restarts the pattern from the d0 block.
              {:pattern #"```wisp\s*"
               :transition :in-d0
               :update-fn (fn [v _m] (start-block v))}
              {:pattern #"```.*"
               :transition :skip}
              ;; Anything else: the blocks are not consecutive; abandon.
              {:transition :init}]
   ;; Collecting the c0 code (`go` block).
   :in-c0 [{:pattern #"```\s*"
            :transition :after-c0
            :update-fn (fn [v _m] (end-block v :c0-code))}
           {:update-fn (fn [v [line]] (add-line v line))}]
   ;; The line immediately after the c0 block must open the result block.
   :after-c0 [{:pattern #"```clojure\s*"
               :transition :in-result
               :update-fn (fn [v _m] (start-block v))}
              {:pattern #"```.*"
               :transition :skip}
              ;; Anything else: the blocks are not consecutive; abandon.
              {:transition :init}]
   ;; Collecting the resulting Clojure code. Closing it fires the callback.
   :in-result [{:pattern #"```\s*"
                :transition :init
                :update-fn (fn [v _m] (fire v))}
               {:update-fn (fn [v [line]] (add-line v line))}]
   ;; Skipping the body of an unrelated code block.
   :skip [{:pattern #"```\s*"
           :transition :init}]})

(defn process-d0-spec
  "Process the given `lines` of a d0 spec, invoking `callback` with the d0
  code, c0 code and resulting Clojure code (as strings) for every recognized
  translation example. Returns the final state map."
  [callback lines]
  (process-lines d0-spec-sm {:state :init :callback callback} lines))

(defn process-d0-spec-file
  "Read the spec at `path` and process it with [[process-d0-spec]]."
  [callback path]
  (process-d0-spec callback (split-lines (slurp path))))

;; The language configurations for the languages used in a d0 spec: `d0` (this
;; project's `lang-conf.edn`) and `c0` (the root project's `lang-conf.edn`).
(def ^:private config-paths ["lang-conf.edn" "../lang-conf.edn"])

(defn- read-config [path]
  (-> path slurp parse-string-all first))

;; The language map, built once, from which `d0` and `c0` code is analyzed. The
;; `y0`-path includes this project (for `d0.y0`) and the root's `y0_test`
;; directory (for `c0.y0`).
(def ^:private lang-map
  (delay
   (binding [*y0-path* [(cwd) (str (cwd) "/../y0_test")]]
     (language-map-from-config (apply merge (map read-config config-paths))))))

(defn- evaluate-statements [ps statements is-main]
  (binding [*skip-recoverable-assertions* (not is-main)]
    (apply-statements statements ps {})))

(defn- analyze [lang code]
  ;; Override `:resolve` with the identity, so that the example module's `ns`
  ;; declaration resolves to its own path (`"example"`).
  (let [lang-map (update @lang-map lang assoc :resolve (fn [name] (ok name)))]
    (let-s [mstore (load-with-deps [{:lang lang
                                     :path "example"
                                     :text code}]
                                   lang-map)
            mstore (eval-mstore mstore evaluate-statements (add-builtins {}))]
           (ok (-> mstore (get "example") :predstore)))))

(defn analyze-d0
  "Analyze d0 `code`, returning a status containing the resulting predicate
  store."
  [code]
  (analyze "d0" code))

(defn analyze-c0
  "Analyze c0 `code`, returning a status containing the resulting predicate
  store."
  [code]
  (analyze "c0" code))

(defn parse-clojure
  "Parse `code` as a list of Clojure s-expressions, returning a status."
  [code]
  (try
    (ok (parse-string-all code))
    (catch Exception e
      {:err ["Error parsing Clojure code:" (.getMessage e)]})))

(defn wrap-callback [f]
  (fn [d0-code c0-code clj-code]
    (f (unwrap-status (analyze-d0 d0-code))
       (unwrap-status (analyze-c0 c0-code))
       (unwrap-status (parse-clojure clj-code)))))

