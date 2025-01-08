(ns y0.edn-parser
  (:require [edamame.core :as e :refer [parse-string-all]]
            [y0.term-utils :refer [postwalk-with-meta]]
            [y0.status :refer [ok]]))

(defn convert-ns [expr ns-map refer-map]
  (postwalk-with-meta
   (fn [expr] (if (symbol? expr)
                (let [meta (meta expr)
                      orig-ns (namespace expr)
                      new-ns (if (and (empty? orig-ns)
                                      (refer-map (name expr)))
                               (refer-map (name expr))
                               (if-let [new-ns (ns-map orig-ns)]
                                 new-ns
                                 (throw (Exception. (str "Undefined namespace: " orig-ns)))))]
                  (-> (symbol new-ns (name expr))
                      (with-meta meta)))
                expr))
   expr))

(defn fold- [[m1 n1 r1] [m2 n2 r2]]
  [(concat m1 m2) (merge n1 n2) (merge r1 r2)])

(defn- handle-directive [args resolve]
  (let [[name & {:keys [as refer]}] args
        name (str name)
        path (resolve name)]
    [[path]
     (if (nil? as)
       {}
       {(str as) path})
     (if (nil? refer)
       {}
       (->> refer
            (map (fn [sym] [(str sym) path]))
            (into {})))]))

(defn- handle-require [vecs resolve]
  (loop [vecs vecs
         res [[] {} {}]]
    (if (empty? vecs)
      res
      (let [[args & vecs] vecs]
        (recur vecs
               (fold- res
                      (handle-directive args resolve)))))))

(defn parse-ns-decl
  ([[_ns module-name & directives] resolve]
   (let [module-name (str module-name)
         fold (fn [[m1 n1 r1] [m2 n2 r2]]
                [(concat m1 m2) (merge n1 n2) (merge r1 r2)])]
     (->> (for [[directive & args] directives]
            (cond
              (= directive ':require) (handle-require args resolve)))
          (reduce fold [[] {nil (resolve module-name)} {}]))))
  ([ns-decl]
   (parse-ns-decl ns-decl identity)))

(defn convert-location [loc]
  (let [{:keys [col row end-col end-row]} loc]
    {:start (+ (* row 1000000) col)
     :end (+ (* end-row 1000000) end-col)}))

(defn- annotate-location [m module-path]
  (if (instance? clojure.lang.IObj (:obj m))
    (with-meta (:obj m) (-> (convert-location (:loc m))
                            (assoc :path module-path)))
    (:obj m)))

(defn edn-parser [root-refer-map lang extra-modules]
  (fn parse
    ([module path text]
     (parse module path text identity))
    ([_module path text resolve]
     (try
       (let [[ns-decl & statements] (parse-string-all text
                                                      {:postprocess #(annotate-location % path)})
             [paths ns-map refer-map] (parse-ns-decl ns-decl resolve)
             refer-map (merge refer-map root-refer-map)
             statements (for [statement statements]
                          (convert-ns statement ns-map refer-map))]
         (if (= path (get ns-map nil))
           (ok [statements (concat paths
                                   extra-modules)])
           {:err ["The module name in the ns declaration in" path
                  "resolved to the wrong path" (get ns-map nil)]}))
       (catch Exception e
         {:err {:error (.getMessage e)}})))))

(defn root-module-symbols [syms ns]
  (->> (for [sym syms]
         [(str sym) ns])
       (into {})))