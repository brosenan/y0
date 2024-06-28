(ns y0.rules
  (:require [clojure.string :refer [join]]
            [y0.core :refer [! !? <- => ? exist fail given trace]]
            [y0.predstore :refer [get-rules-to-match get-statements-to-match
                                  match-rule store-rule store-statement
                                  store-translation-rule]]
            [y0.status :refer [->s let-s ok]]
            [y0.term-utils :refer [replace-vars]]
            [y0.unify :refer [unify reify-term]]))

(def ^:dynamic *do-trace* false)
(def ^:dynamic *trace-indent* ">")

(defn new-vars [bindings symbols]
  (loop [bindings bindings
         [sym & syms] symbols]
    (if (nil? sym)
      bindings
      (-> bindings 
          (assoc sym (atom nil))
          (recur syms)))))

(defn split-goal [goal why-not]
  (let [bang-index (.indexOf goal `!)] 
    (if (= bang-index -1)
      [goal why-not]
      [(take bang-index goal) (-> bang-index inc (drop goal) vec)])))

(declare satisfy-goal check-conditions check-condition add-rule apply-statement)

(defn- nest-explanation [why-not cause-why-not vars]
  (if (nil? why-not)
    cause-why-not
    (let [vars (assoc vars `!? cause-why-not)]
      (replace-vars why-not vars))))

(defn- check-fail-condition [condition ps vars why-not]
  (let [[condition why-not] (split-goal condition why-not)
        [_fail cond-to-fail op & expected-why-not] condition
        expected-why-not (vec (replace-vars expected-why-not vars))
        status (check-condition cond-to-fail ps vars why-not)]
    (if (:err status)
      (if (= op `?)
        (if (unify expected-why-not (:err status))
          {:ok nil}
          (if (nil? why-not)
            status
            {:err (nest-explanation why-not (:err status) vars)}))
        {:ok nil})
      {:err (nest-explanation why-not 
                              [(replace-vars cond-to-fail vars) "succeeded when it should have failed"]
                              vars)})))

(defn- check-condition [condition ps vars why-not]
  (when *do-trace*
    (println *trace-indent* 
             (-> condition (replace-vars vars) reify-term)))
  (let [res (with-bindings {#'*trace-indent* (str *trace-indent* "  ")}
              (cond
                (-> condition first (= `exist)) (let [[_exist bindings & conditions] condition
                                                      vars (new-vars vars bindings)]
                                                  (check-conditions conditions ps vars))
                (-> condition first (= `given)) (let-s [[_given statement & conditions] (ok condition)
                                                        ps' (apply-statement statement ps vars)]
                                                       (check-conditions conditions ps' vars))
                (-> condition first (= `fail)) (check-fail-condition condition ps vars why-not)
                (-> condition first (= `?)) (do
                                              (apply println "?" (replace-vars (rest condition) vars))
                                              {:ok nil})
                (-> condition first (= `trace)) (let [[_trace & conditions] condition]
                                                  (with-bindings {#'*do-trace* true}
                                                    (check-conditions conditions ps vars)))
                :else (let [condition (replace-vars condition vars)]
                        (satisfy-goal ps condition why-not))))]
    (when *do-trace*
      (println *trace-indent*
               res))
    res))

(defn- check-conditions [conditions ps vars]
  (if (empty? conditions)
    (ok nil)
    (let-s [[condition & conditions] (ok conditions)
            _ (check-condition condition ps vars nil)]
           (recur conditions ps vars))))

(defn- add-deduction-rule [ps bindings head conditions vars]
  (let [[head why-not] (split-goal head nil)
        vars' (new-vars vars bindings)
        head' (replace-vars head vars')
        body (if why-not
               (fn [goal _why-not _ps]
                 (let [vars (new-vars vars bindings)
                       why-not (replace-vars why-not vars)
                       head (replace-vars head vars)]
                   (unify goal head)
                   {:err why-not}))
               (fn [goal why-not ps]
                 (let [vars (new-vars vars bindings)
                       head (replace-vars head vars)]
                   (if (unify goal head)
                     (check-conditions conditions ps vars)
                     {:err why-not}))))]
    (store-rule ps head' body)))

(defn- expect-status [status why-not test vars primary]
  (if (nil? why-not)
    (cond
      (contains? status :err) (if primary
                                {:err (concat (:err status) ["in assertion" test])}
                                status)
      :else status)
    (cond
      (contains? status :ok) {:err
                              ["Expected failure for goal" (replace-vars test vars) "but it succeeded"]}
      (not (unify (:err status) (replace-vars why-not vars)))
      {:err
       ;; TODO: (concat ["Wrong explanation is given:"] (:err status) ["instead of"] why-not)
       ["Wrong explanation is given:" (:err status) "instead of" why-not]}
      :else (ok nil))))

(defn apply-assert-block [ps assert-block vars]
  (let [[_assert & assertions] assert-block]
    (loop [assertions assertions]
      (let [[assertion & assertions] assertions]
        (if (nil? assertion)
          (ok ps)
          (let-s [[assertion why-not] (ok  assertion split-goal nil)
                  _nil (expect-status
                        (check-condition assertion ps vars 
                                         ["Test failed without explanation"])
                        why-not assertion vars
                        (-> assert-block meta :origin nil?))]
                 (recur assertions)))))))

(defn- apply-normal-statement [ps statement vars]
  (let-s [statement (ok statement replace-vars vars)
          ps (store-statement ps statement)]
    (loop [trans-rules (seq (get-rules-to-match ps statement))
           ps ps]
      (if (empty? trans-rules)
        (ok ps)
        (let-s [[rule & rules] (ok trans-rules)
                ps (rule statement ps)]
               (recur rules ps))))))

(defn- apply-statement [statement ps vars]
  (let [[form & _] statement]
    (case form
      y0.core/all (add-rule ps statement vars)
      y0.core/assert (apply-assert-block ps statement vars)
      ;; Debugging utility. Keeping for the time being.
      y0.core/? (let [[_? pred arity] statement
                      pd (get ps {:name (str pred) :arity arity})]
                  (println "?" pd)
                  {:ok ps})
      (apply-normal-statement ps statement vars))))

(defn apply-statements [statements ps vars]
  (loop [statements statements
         ps ps]
    (if (empty? statements)
      (ok ps)
      (let [[statement & statements] statements]
        (let-s [ps (apply-statement statement ps vars)]
               (recur statements ps))))))

(defn- apply-rule-to-statements [ps head body]
  (loop [statements (seq (get-statements-to-match ps head))
         ps ps]
    (if (empty? statements)
      (ok ps)
      (let-s [ps (body (first statements) ps)]
             (recur (rest statements) ps)))))

(defn- add-translation-rule [ps bindings head terms vars]
  (let [vars' (new-vars vars bindings)
        head' (replace-vars head vars')
        body (fn [statement ps]
               (let [terms (map #(vary-meta % assoc :origin statement) terms)
                     vars (new-vars vars bindings)
                     head (replace-vars head vars)]
                 (if (unify statement head)
                   (apply-statements terms ps vars)
                   (ok ps))))]
    (->s (store-translation-rule ps head' body)
         (apply-rule-to-statements head' body))))

(defn add-rule [ps rule vars]
  (let [[_all bindings head op & terms] rule]
    (cond
      (or (nil? op) (= op `<-)) (add-deduction-rule ps bindings head terms vars)
      (= op `=>) (add-translation-rule ps bindings head terms vars)
      :else {:err ["Invalid rule operator" op]})))

(defn satisfy-goal [ps goal why-not]
  (let [[goal why-not] (split-goal goal why-not)]
    (let-s [rule (match-rule ps goal)]
           (rule goal why-not ps))))
