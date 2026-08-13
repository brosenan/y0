# Expressions

The examples in this sections will define a `D0` `root-expr` and then a dummy
$C_0$ function. The resulting Clojure code will be based on the `D0` root
expression, disregarding the body of the $C_0$ function.

For example:

```wisp
(ns example)

(root-expr "test" [node]
  42)
```

And here is an example:
```go
int32 test() { return 999; }
```
```clojure
(clojure.core/defn test [] 42)
```

## Literals

`D0` literals are processed as literals in resulting Clojure code.

```wisp
(ns example)

(root-expr "test" [node]
  3.141592)
```

And here is an example:
```go
float64 test() { return 0.0; }
```
```clojure
(clojure.core/defn test [] 3.141592)
```

## `let` Expressions

`D0` `let` expressions are translated to Clojure `let` expressions.

```wisp
(ns example)

(root-expr "test" [node]
  (let [a 1
        b a]
    b))
```

And here is an example:
```go
int64 test() { return 0; }
```
```clojure
(clojure.core/defn test []
  (clojure.core/let [a 1
                     b a]
    b))
```
