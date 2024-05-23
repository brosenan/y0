(ns y0.status)

(defn unwrap-status [{:keys [ok err]}]
  (if (nil? err)
    ok
    (throw (Exception. (str err)))))

(defmacro ok [arg f & args]
  {:ok `(~f ~arg ~@args)})

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