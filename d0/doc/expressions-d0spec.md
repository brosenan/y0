# Expressions

Here is some `D0` code:
```wisp
(ns example)

(root-expr "test" [node]
  42)
```

And here is an example:
```go
int32 test() { return 42; }
```
```clojure
(clojure.core/defn test [] 42)
```
