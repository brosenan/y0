* [Built-ins](#built-ins)
  * [`=`](#`=`)
  * [`inspect`](#`inspect`)
  * [Conversion Predicates](#conversion-predicates)
```clojure
(ns builtins)

```
# Built-ins

In this module we define the built-in predicates that are provided by $y_0$.

## `=`

`=` is the unification predicate. It takes two arguments and succeeds if
they unify.
```clojure
(assert
 (= 1 1)
 (= 1 2 ! "Test failed without explanation"))

```
## `inspect`

`inspect` takes an s-expression and a _kind_, which is bound to a keyword
that corresponds to the kind of s-expression the first argument is of.
```clojure
(assert
 (inspect 42 :int)
 (inspect 3.141592 :float)
 (inspect "foo" :string)
 (inspect 'foo :symbol)
 (inspect :foo :keyword)
 (inspect [1 2 3] :vector)
 (inspect (1 2 3) :list)
 (inspect #{a b c} :set)
 (inspect {foo bar} :map))

```
`replace-meta` is $y_0$'s way of dealing with meta variables. Meta variables
are free variables that are introduced by the input, the AST being analyzed
rather than by the rules themselves. Because they are data, they can be
handled by a builtin predicate.

`replace-meta` takes a list or a vector of varaibles symbols, a term that
contains these symbols and a similar term, in which it will replace the
variable symbols with actual variables.
```clojure
(assert
 (replace-meta [a b] [[a b] [b a]] [[1 2] [2 1]])
 (replace-meta [a b] [[a b] [b a]] [[1 2] [2 3]] ! "Test failed without explanation"))

```
The variable symbols can be introduced in a list or a vector.
```clojure
(assert
 (replace-meta (a b) [[a b] [b a]] [[1 2] [2 1]])
 (replace-meta (a b) [[a b] [b a]] [[1 2] [2 3]] ! "Test failed without explanation"))

```
The variable symbol list may, in part or in full, consist of bound variables.
```clojure
(assert
 (exist [l]
        (= l [a b])
        (replace-meta l [[a b] [b a]] [[1 2] [2 1]]))
 (exist [x]
        (= x b)
        (replace-meta [a x] [[a b] [b a]] [[1 2] [2 1]])))

```
The term that contains the symbols may be constructed of bound variables.
```clojure
(assert
 (exist [from to]
        (= from [a b])
        (= to [b a])
        (replace-meta [a b] [from to] [[1 2] [2 1]])))

```
## Conversion Predicates

`to-list` takes a collection and converts it to a list.
```clojure
(assert
 (to-list [1 2 3] (1 2 3))
 (to-list (1 2 3) (1 2 3)))

```
Similarly, `to-vec` converts to a vector.
```clojure
(assert
 (to-vec (1 2 3) [1 2 3])
 (to-vec [1 2 3] [1 2 3]))
```

