(ns d0.d0-spec-analyzer
  (:require [clojure.string :refer [join split-lines]]
            [y0.spec-analyzer :refer [process-lines]]))

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
  {;; Outside any example. Look for the opening `clojure` fence.
   :init [{:pattern #"```clojure\s*"
           :transition :in-d0
           :update-fn (fn [v _m] (start-block v))}
          {:pattern #"```.*"
           :transition :skip}]
   ;; Collecting the d0 code (first `clojure` block).
   :in-d0 [{:pattern #"```\s*"
            :transition :after-d0
            :update-fn (fn [v _m] (end-block v :d0-code))}
           {:update-fn (fn [v [line]] (add-line v line))}]
   ;; The line immediately after the d0 block must open the `go` block.
   :after-d0 [{:pattern #"```go\s*"
               :transition :in-c0
               :update-fn (fn [v _m] (start-block v))}
              ;; A new `clojure` fence restarts the pattern from the d0 block.
              {:pattern #"```clojure\s*"
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
