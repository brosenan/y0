(ns y0.core)

(defmacro def-symbols [& symbols]
  `(do
     ~@(for [sym symbols]
         `(def ~sym (quote ~sym)))
     (def y0-symbols ~(vec symbols))))

(def-symbols
  all
  assert
  with-meta
  exist
  given
  trace
  &
  <-
  =>
  !
  ?
  !?
  ;; Built-ins
  =
  inspect
  replace-meta
  to-list
  to-vec
  symbolize
  length)

(def y0-root-refer-map
  (->> (for [sym y0-symbols]
         [(name sym) "y0.core"])
       (into {})))