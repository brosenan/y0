(ns d0.d0-spec-analyzer
  (:require [clojure.string :refer [join split-lines]]
            [edamame.core :refer [parse-string-all]]
            [y0.builtins :refer [add-builtins]]
            [y0.config :refer [*y0-path* cwd language-map-from-config]]
            [y0.polyglot-loader :refer [eval-mstore load-with-deps]]
            [y0.rules :refer [*skip-recoverable-assertions* apply-statements]]
            [y0.spec-analyzer :refer [process-lines]]
            [y0.status :refer [ok let-s]]
            [y0.term-utils :refer [postwalk-meta]]
            [y0.unify :refer [reify-term]]))

(defn- start-block [v]
  (-> v
      (assoc :current-block [])
      ;; Record the line of the opening fence, so that error locations within
      ;; the block can be mapped back to the spec file.
      (assoc :block-line (:line v))))

(defn- add-line [v line]
  (update v :current-block (fnil conj []) line))

(defn- end-block [v code-key line-key]
  (-> v
      (assoc code-key (join "\n" (:current-block v)))
      (assoc line-key (:block-line v))
      (dissoc :current-block :block-line)))

;; Each code block is analyzed with its language name as its `:path` (`"d0"`,
;; `"c0"` or `"clojure"`). `convert-locations` maps such locations in an
;; explanation back to the spec file, offsetting the row by the line of the
;; block's opening fence.
(defn- convert-locations [explanation base-lines spec-path]
  (->> explanation
       reify-term
       (postwalk-meta (fn [{:keys [path start end] :as m}]
                        (if-let [base (get base-lines path)]
                          {:path spec-path
                           :start (+ start (* base 1000000))
                           :end (+ end (* base 1000000))}
                          m)))))

(defn- fire [v]
  (when-not (contains? v :last-d0)
    (throw (Exception.
            "A translation example must be preceded by a d0 (wisp) block, but none was found")))
  (let [clj-code (join "\n" (:current-block v))
        clj-line (:block-line v)
        status ((:callback v) (:last-d0 v) (:c0-code v) clj-code)
        v (dissoc v :current-block :block-line :c0-code)]
    (if (contains? status :err)
      (let [base-lines {"d0" (:last-d0-line v)
                        "c0" (:c0-line v)
                        "clojure" clj-line}
            err (convert-locations (:err status) base-lines (:path v))]
        (update v :errors (fnil conj []) err))
      (update v :success (fnil inc 0)))))

;; The state machine treats a `wisp` (d0) block as a free-standing prerequisite,
;; stored under `:last-d0`. An example consists of a `go` (c0) block strictly
;; followed by a `clojure` (result) block; closing the result block fires the
;; callback with the most recent `:last-d0`.
(def d0-spec-sm
  {;; Count lines at any state, so blocks can record their opening-fence line.
   :any [{:update-fn (fn [v _m] (update v :line (fnil inc 0)))}]
   ;; Outside any block. A `wisp` block updates the d0 prerequisite; a `go`
   ;; block starts an example.
   :init [{:pattern #"```wisp\s*"
           :transition :in-d0
           :update-fn (fn [v _m] (start-block v))}
          {:pattern #"```go\s*"
           :transition :in-c0
           :update-fn (fn [v _m] (start-block v))}
          {:pattern #"```.*"
           :transition :skip}]
   ;; Collecting a d0 (`wisp`) block. On close it becomes the `:last-d0`
   ;; prerequisite, and does not, by itself, produce an example.
   :in-d0 [{:pattern #"```\s*"
            :transition :init
            :update-fn (fn [v _m] (end-block v :last-d0 :last-d0-line))}
           {:update-fn (fn [v [line]] (add-line v line))}]
   ;; Collecting the c0 (`go`) block of an example.
   :in-c0 [{:pattern #"```\s*"
            :transition :after-c0
            :update-fn (fn [v _m] (end-block v :c0-code :c0-line))}
           {:update-fn (fn [v [line]] (add-line v line))}]
   ;; The line immediately after the c0 block must open the `clojure` result
   ;; block.
   :after-c0 [{:pattern #"```clojure\s*"
               :transition :in-result
               :update-fn (fn [v _m] (start-block v))}
              ;; A `wisp` block updates the prerequisite (this example is
              ;; abandoned).
              {:pattern #"```wisp\s*"
               :transition :in-d0
               :update-fn (fn [v _m] (start-block v))}
              ;; A new `go` block starts a new example.
              {:pattern #"```go\s*"
               :transition :in-c0
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
  "Process the given `lines` of a d0 spec. A `wisp` block updates the current d0
  prerequisite. For every example -- a `go` block strictly followed by a
  `clojure` block -- `callback` is invoked with the most recent d0 code, the c0
  code and the resulting Clojure code (as strings), and is expected to return a
  status. Successful examples are counted under `:success` in the returned state,
  while the explanations of failed ones are collected under `:errors`. `path`, if
  given, is the spec file path, used to report error locations."
  ([callback lines]
   (process-d0-spec callback lines nil))
  ([callback lines path]
   (process-lines d0-spec-sm {:state :init :callback callback :path path} lines)))

(defn process-d0-spec-file
  "Read the spec at `path` and process it with [[process-d0-spec]]."
  [callback path]
  (process-d0-spec callback (split-lines (slurp path)) path))

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
  ;; Use the language name as the module's `:path`, so that error locations can
  ;; be attributed to the right code block (see `convert-locations`). Override
  ;; `:resolve` to return this path regardless of the module name, so that the
  ;; module's `ns` declaration resolves to its own path.
  (let [lang-map (update @lang-map lang assoc :resolve (constantly (ok lang)))]
    (let-s [mstore (load-with-deps [{:lang lang
                                     :path lang
                                     :text code}]
                                   lang-map)
            mstore (eval-mstore mstore evaluate-statements (add-builtins {}))]
           (ok (-> mstore (get lang) :predstore)))))

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
    (let-s [d0-ps (analyze-d0 d0-code)
            c0-ps (analyze-c0 c0-code)
            sexprs (parse-clojure clj-code)]
           (f d0-ps c0-ps sexprs))))

