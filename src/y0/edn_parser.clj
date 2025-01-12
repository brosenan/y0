(ns y0.edn-parser
  (:require [edamame.core :as e :refer [parse-string-all]]
            [y0.term-utils :refer [postwalk-with-meta]]
            [y0.status :refer [ok let-s]]))

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

(defn- handle-normal-directive [args [paths ns-map refer-map] resolve]
  (let [[name & {:keys [as refer]}] args
        name (str name)]
    (let-s [path (resolve name)
            path (ok (str path))]
           (ok [(conj paths path)
                (if (nil? as)
                  ns-map
                  (assoc ns-map (str as) path))
                (if (nil? refer)
                  refer-map
                  (merge refer-map (->> refer
                                        (map (fn [sym] [(str sym) path]))
                                        (into {}))))]))))

(defn- handle-require [vecs res resolve]
  (loop [vecs vecs
         res res]
    (if (empty? vecs)
      (ok res)
      (let-s [[args & vecs] (ok vecs)
              res (handle-normal-directive args res resolve)]
             (recur vecs res)))))

(defn- handle-directive [[name & vecs] res resolve]
  (cond
    (= name :require) (handle-require vecs res resolve)
    :else {:err ["Invalid ns directive" name]}))

(defn parse-ns-decl [[_ns module-name & directives] resolve]
  (let-s [module-name (ok (str module-name))
          module-path (resolve module-name)
          module-path (ok (str module-path))]
         (loop [directives directives
                res [[] {nil module-path} {}]]
           (if (empty? directives)
             (ok res)
             (let-s [[directive & directives] (ok directives)
                     res (handle-directive directive res resolve)]
                    (recur directives res))))))

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
    ([path text resolve]
     (try
       (let-s [[ns-decl & statements] (ok (parse-string-all text
                                                            {:postprocess #(annotate-location % path)}))
               [paths ns-map refer-map] (parse-ns-decl ns-decl resolve)
               refer-map (ok (merge refer-map root-refer-map))
               statements (ok (for [statement statements]
                                (convert-ns statement ns-map refer-map)))]
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