(ns y0.builtins 
  (:require [clojure.string :as str]
            [y0.rules :refer [new-vars]]
            [y0.term-utils :refer [replace-vars]]
            [y0.unify :refer [reify-term unify]]))

(defn- unify-or-err [a b why-not]
  (if (unify a b)
      {:ok nil}
      {:err why-not}))

(defn eq [goal why-not _ps]
  (let [[_= a b] goal]
    (unify-or-err a b why-not)))

(defn kind-of [term]
  (cond
    (instance? clojure.lang.Atom term) (recur @term)
    (int? term) :int
    (double? term) :float
    (string? term) :string
    (symbol? term) :symbol
    (keyword? term) :keyword
    (vector? term) :vector
    (seq? term) :list
    (set? term) :set
    (map? term) :map
    :else :unknown))

(defn inspect [goal why-not _ps]
  (let [[_inspect term kind] goal
        inspected (kind-of term)]
    (unify-or-err kind inspected why-not)))

(defn replace-meta [goal why-not _ps]
  (let [[_replace-meta bindings symbolic term] goal
        bindings (reify-term bindings)
        symbolic (reify-term symbolic)
        vars (new-vars {} bindings)
        term' (replace-vars symbolic vars)]
    (unify-or-err term term' why-not)))

(defn gen-to-x [ctor]
  (fn [goal why-not _ps]
    (let [[_pred term var] goal
          term (reify-term term)]
      (unify-or-err (ctor term) var why-not))))

(defn symbolize [goal why-not _ps]
  (let [[_symbolize base values newsym] goal
        base (reify-term base)
        values (reify-term values)
        name-prefix (if (symbol? base)
                      [(name base)]
                      [])
        newname (str/join "-" (concat name-prefix (map str values)))
        ns (if (symbol? base)
             (namespace base)
             (str base))]
    (unify-or-err newsym (symbol ns newname) why-not)))

(defn add-builtin [ps name arity func]
  (assoc ps {:name (str "y0.core/" name) :arity arity} {{} func}))

(defn add-builtins [ps]
  (-> ps
      (add-builtin "=" 2 eq)
      (add-builtin "inspect" 2 inspect)
      (add-builtin "replace-meta" 3 replace-meta)
      (add-builtin "to-list" 2 (gen-to-x seq))
      (add-builtin "to-vec" 2 (gen-to-x vec))
      (add-builtin "symbolize" 3 symbolize)))
