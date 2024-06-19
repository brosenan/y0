(ns y0.status)

(defn unwrap-status [{:keys [ok err]}]
  (if (nil? err)
    ok
    (throw (Exception. (str err)))))

(defmacro ok
  ([arg f & args]
   {:ok `(~f ~arg ~@args)})
  ([value] {:ok value}))

(defmacro ->s [expr & exprs]
  (if (empty? exprs)
    expr
    (let [val (gensym "val")]
      `(let [~val ~expr]
         ~(let [[next-expr & exprs] exprs
                [func & args] next-expr]
            `(if (nil? (:err ~val))
               (->s (~func (:ok ~val) ~@args)
                    ~@exprs)
               ~val))))
    
    ))

(defn update-with-status [m k f ef]
  (let [v (get m k)
        {:keys [ok err]} (f v)]
    (if (nil? err)
      {:ok (assoc m k ok)}
      {:err (ef err k)})))

(defmacro let-s [bindings expr]
  (if (empty? bindings)
    expr
    (let [[var b-expr & bindings] bindings
          status-var (gensym "status-var")]
      `(let [~status-var ~b-expr]
         (if (nil? (:err ~status-var))
           (let [~var (:ok ~status-var)]
             (let-s ~(vec bindings) ~expr))
           ~status-var)))))