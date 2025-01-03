(ns y0.rules
  (:require
   [y0.core :refer [! !? <- => ? exist given trace]]
   [y0.predstore :refer [get-exports get-rules-to-match get-statements-to-match
                         match-rule store-export store-rule store-statement
                         store-translation-rule]]
   [y0.status :refer [->s let-s ok]]
   [y0.term-utils :refer [ground? replace-vars]]
   [y0.unify :refer [reify-term unify]]))

(def ^:dynamic *do-trace* false)
(def ^:dynamic *trace-indent* ">")
(def ^:dynamic *subject* nil)
(def ^:dynamic *update-decor* nil)
(def ^:dynamic *error-target* nil)
(def ^:dynamic *skip-recoverable-assertions* false)
(defn new-vars [bindings symbols]
  (loop [bindings bindings
         [sym & syms] symbols]
    (if (nil? sym)
      bindings
      (-> bindings 
          (assoc sym (atom nil))
          (recur syms)))))

(defn- combine-explanations [cause effect]
  (if (nil? effect)
    cause
    (vec (let [bangq-index (.indexOf effect `!?)]
           (if (= bangq-index -1)
             (concat cause effect)
             (concat (take bangq-index effect) cause (-> bangq-index inc (drop effect))))))))

(defn split-goal [goal why-not]
  (let [bang-index (.indexOf goal `!)] 
    (if (= bang-index -1)
      [goal why-not]
      [(take bang-index goal) (-> bang-index inc (drop goal) (combine-explanations why-not) vec)])))

(declare satisfy-goal check-conditions check-condition add-rule apply-statement
         apply-statements)

(defn- check-given-condition [condition why-not vars ps]
  (let-s [[_given statement & conditions] (ok condition)
          statement (ok statement vary-meta assoc :def *subject*)
          ps' (apply-statement statement ps vars)]
         (check-conditions conditions ps' vars why-not)))

(defn- check-condition [condition ps vars why-not]
  (when *do-trace*
    (println *trace-indent* 
             (-> condition (replace-vars vars) reify-term)))
  (let [res (with-bindings {#'*trace-indent* (str *trace-indent* "  ")}
              (cond
                (not (seq? condition)) {:err ["invalid condition" condition]}
                (-> condition first (= `exist)) (let [[_exist bindings & conditions] condition
                                                      vars (new-vars vars bindings)]
                                                  (check-conditions conditions ps vars why-not))
                (-> condition first (= `given)) (check-given-condition condition why-not vars ps)
                (-> condition first (= `?)) (do
                                              (apply println "?" (replace-vars (rest condition) vars))
                                              {:ok nil})
                (-> condition first (= `trace)) (let [[_trace & conditions] condition]
                                                  (with-bindings {#'*do-trace* true}
                                                    (check-conditions conditions ps vars why-not)))
                :else (let [condition (replace-vars condition vars)]
                        (satisfy-goal ps condition why-not))))]
    (when *do-trace*
      (println *trace-indent*
               res))
    res))

(defn- check-conditions [conditions ps vars why-not]
  (if (empty? conditions)
    (ok nil)
    (let-s [[condition & conditions] (ok conditions)
            _ (check-condition condition ps vars why-not)]
           (recur conditions ps vars why-not))))

(defn- resolve-subject [why-not]
  (replace {`? *subject*} why-not))

(defn report-err [why-not]
  (when (some? *update-decor*)
    (*update-decor* #(assoc % :err why-not)))
  {:err why-not})

(defn- add-deduction-rule [ps bindings head conditions vars def]
  (let [[head why-not] (split-goal head nil)
        vars' (new-vars vars bindings)
        head' (replace-vars head vars')
        body (if why-not
               (fn [goal why-not-context _ps]
                 (let [vars (new-vars vars bindings)
                       why-not (-> why-not
                                   (replace-vars vars)
                                   resolve-subject
                                   (combine-explanations why-not-context)
                                   vec)
                       head (replace-vars head vars)]
                   (unify goal head)
                   (report-err why-not)))
               (fn [goal why-not ps]
                 (let [vars (new-vars vars bindings)
                       head (replace-vars head vars)]
                   (when *do-trace*
                     (println *trace-indent* goal head (unify goal head)))
                   (if (unify goal head)
                     (with-bindings {#'*subject* (second goal)}
                       (check-conditions conditions ps vars why-not))
                     (report-err why-not)))))
        body (with-meta body {:def def})]
    (store-rule ps head' body)))

(defn- expect-status [status why-not test vars primary recoverable]
  (if (nil? why-not)
    (cond
      (contains? status :err) (let [status (if primary
                                             {:err (concat (:err status) ["in assertion" test])}
                                             status)]
                                (if (and recoverable
                                         (some? *error-target*))
                                  (do
                                    (swap! *error-target* conj status)
                                    {:ok nil})
                                  status))
      :else status)
    (cond
      (contains? status :ok) {:err
                              ["Expected failure for goal" (replace-vars test vars) "but it succeeded"]}
      (not (unify (:err status) (replace-vars why-not vars)))
      {:err
       ;; TODO: (concat ["Wrong explanation is given:"] (:err status) ["instead of"] why-not)
       ["Wrong explanation is given:" (:err status) "instead of" why-not]}
      :else (ok nil))))

(defn apply-assert-block [ps assert-block vars recoverable]
  (let [[_assert & assertions] assert-block
        origin (-> assert-block meta :origin)
        why-not-context (if (nil? origin)
                          []
                          ["in" origin])]
    (if (and *skip-recoverable-assertions*
             recoverable)
      {:ok ps}
      (loop [assertions assertions]
        (let [[assertion & assertions] assertions]
          (if (nil? assertion)
            (ok ps)
            (let-s [[assertion why-not] (ok assertion split-goal nil)
                    _nil (expect-status
                          (check-condition assertion ps vars why-not-context)
                          why-not assertion vars
                          (nil? origin)
                          recoverable)]
                   (recur assertions))))))))

(defn apply-normal-statement [ps statement vars]
  (let-s [statement (ok statement replace-vars vars)
          ps (store-statement ps statement)
          trans-rules (ok (seq (get-rules-to-match ps statement)))]
         (if (and (empty? trans-rules)
                  (not= (first statement) `all)
                  (not= (first statement) `fact)
                  (not= (first statement) `assert))
           {:err ["No rules are defined to translate statement" statement
                  "and therefore it does not have any meaning"]}
           (loop [trans-rules trans-rules
                  ps ps]
             (if (empty? trans-rules)
               (ok ps)
               (let-s [[rule & rules] (ok trans-rules)
                       ps (rule statement ps)]
                      (recur rules ps)))))))

(defn- apply-with-meta-block [ps statement vars]
  (let-s [[_with-meta bindings statement] (ok statement)
          metavars (loop [metavars {}
                          [mvar val & bindings] bindings]
                     (if (nil? mvar)
                       (ok metavars)
                       (let [val (replace-vars val vars)]
                         (if (ground? val)
                           (recur (assoc metavars mvar val)
                                  bindings)
                           {:err [mvar "has non-ground value" val]}))))
          statement (-> statement (replace-vars metavars) reify-term ok)]
    (apply-statement statement ps vars)))

(defn- symname [sym]
  (if (symbol? sym)
    (name sym)
    sym))

(defn- add-export [ps statement vars]
  (let [{:keys [def]} (meta statement)
        statement (rest statement)  ;; Remove export
        [keys statement] (if (set? (first statement))
                           [(first statement) (rest statement)]
                           [#{} statement])
        keys (->> keys
                  (map #(-> %
                            (replace-vars vars)
                            reify-term
                            symname))
                  (cons :all))
        [[imp exp] & statements] statement
        exp-sym (reify-term (replace-vars exp vars))
        ps (store-export ps (namespace exp-sym) keys
                         (fn [ps impns]
                           (let [imp-sym (symbol impns (name exp-sym))
                                 vars (assoc vars imp imp-sym)
                                 statements (->> statements
                                                 (map #(vary-meta % assoc :def def)))]
                             (apply-statements statements ps vars))))]
    {:ok ps}))

(defn- apply-import [ps statement vars]
  (let [[statement why-not] (split-goal statement nil)
        statement (-> statement (replace-vars vars) reify-term)
        [_import sym key] statement
        key (if (nil? key) :all key)
        impns (namespace sym)
        expns (name sym)
        export-fns (get-exports ps expns (symname key))]
    (if (nil? export-fns)
      {:err why-not}
      (loop [fns export-fns
             ps ps]
        (if (empty? fns)
          {:ok ps}
          (let-s [[f & fns] (ok fns)
                  ps (f ps impns)]
                 (recur fns ps)))))))

(defn- add-fact [ps [_fact fact] vars]
  (add-rule ps `(y0.core/all [] ~fact) vars))

(defn apply-statement [statement ps vars]
  (cond
    (contains? vars statement) (recur @(get vars statement) ps vars)
    (sequential? statement) (let [[form & _] statement]
                              (case form
                                y0.core/all (add-rule ps statement vars)
                                y0.core/fact (add-fact ps statement vars)
                                y0.core/assert (apply-assert-block ps statement vars true)
                                y0.core/assert! (apply-assert-block ps statement vars false)
                                y0.core/with-meta (apply-with-meta-block ps statement vars)
                                y0.core/export (add-export ps statement vars)
                                y0.core/import (apply-import ps statement vars)
                                ;; Debugging utility. Keeping for the time being.
                                y0.core/? (let [[_? key] statement]
                                            (if (nil? key)
                                              (println "?" (keys ps))
                                              (let [pd (get ps key)]
                                                (println "?" pd)))
                                            {:ok ps})
                                (apply-normal-statement ps statement vars)))
    :else {:err ["Invalid statement" statement]}))

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

(defn- origin-of [statement]
  (if-let [origin (-> statement meta :origin)]
    origin
    statement))

(defn- decorated? [node]
  (-> node meta :matches nil? not))

(defn- find-definition [node]
  (cond
    (instance? clojure.lang.IAtom node) (recur @node)
    (decorated? node) node
    (sequential? node) (recur (second node))
    :else nil))

(defn- add-translation-rule [ps bindings head terms vars]
  (let [vars' (new-vars vars bindings)
        head' (replace-vars head vars')
        body (fn [statement ps]
               (let [definition (or (find-definition statement)
                                    (-> statement meta :origin))
                     terms (map (fn [x]
                                  (vary-meta
                                   x #(-> %
                                          (assoc :origin (origin-of statement))
                                          (assoc :def definition)))) terms)
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
      (or (nil? op) (= op `<-)) (add-deduction-rule ps bindings head terms vars
                                                    (-> rule meta :def))
      (= op `=>) (add-translation-rule ps bindings head terms vars)
      :else {:err ["Invalid rule operator" op]})))

(defn- update-decorations [goal rule]
  (let [[pred subj & args] goal
        subj (if (instance? clojure.lang.IAtom subj)
               @subj
               subj)
        {:keys [matches]} (meta subj)
        def (-> rule meta :def)
        {:keys [refs]} (meta def)]
    (when (some? matches)
      (swap! matches assoc pred {:args args
                                 :def def}))
    (when (some? refs)
      (swap! refs conj subj))
    (if (some? matches)
      #(swap! matches update pred %)
      (constantly nil))))

(defn satisfy-goal [ps goal why-not]
  (let [[goal why-not] (split-goal goal why-not)
        why-not (resolve-subject why-not)]
    (let-s [rule (match-rule ps goal)]
           (let [update-decor (update-decorations goal rule)]
             (binding [*update-decor* update-decor]
               (rule goal why-not ps))))))
