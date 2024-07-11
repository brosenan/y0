(ns y0.core)

(defmacro def-symbols [& symbols]
  `(do
     ~@(for [sym symbols]
         `(def ~sym (quote ~sym)))
     (def y0-symbols ~(vec symbols))))

(def-symbols
  all
  assert
  exist
  given
  fail
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
  to-vec)
