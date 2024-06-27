* [Built-ins](#built-ins)
  * [`inspect`](#`inspect`)
```clojure
(ns builtins)

```
# Built-ins

In this module we define the built-in predicates that are provided by $y_0$.

## `inspect`

`inspect` takes an s-expression and a _kind_, which is bound to a keyword
that corresponds to the kind of s-expression the first argument is of.
```clojure
(assert
 (inspect 42 :int)
 (fail (inspect 42 :float)) ;; Just making sure this is not random...
 (inspect 3.141592 :float)
 (inspect "foo" :string)
 (inspect 'foo :symbol)
 (inspect :foo :keyword)
 (inspect [1 2 3] :vector)
 (inspect (1 2 3) :list)
 (inspect #{a b c} :set)
 (inspect {foo bar} :map))
```

