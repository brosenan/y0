(ns y0.term-utils)

(defn postwalk-with-meta [f x]
  (let [m (meta x)
        x' (cond
             (vector? x) (->> x (map #(postwalk-with-meta f %)) vec)
             (seq? x) (->> x (map #(postwalk-with-meta f %)) sequence)
             (map? x) (->> x (map #(postwalk-with-meta f %)) (into {}))
             (set? x) (->> x (map #(postwalk-with-meta f %)) set)
             :else x)
        y (f x')]
    (if (instance? clojure.lang.IObj y)
      (with-meta y m)
      y)))

(defn replace-vars [term vars]
  (postwalk-with-meta #(get vars % %) term))

(defn ground? [term]
  (cond
    (nil? term) false
    (coll? term) (every? ground? term)
    (instance? clojure.lang.Atom term) (recur @term)
    :else true))

(defn replace-ground-vars [term vars]
  (let [term (postwalk-with-meta (fn [x]
                                   (if (and (contains? vars x)
                                            (ground? @(get vars x)))
                                     @(get vars x)
                                     x)) term)
        vars (->> vars
                  (filter #(-> % second ground? not))
                  (into {}))]
    [term vars]))
