```clojure
(ns y0lsp.node-nav-test
  (:require
   [midje.sweet :refer [fact]] 
   [y0lsp.node-nav :refer :all]
   [y0lsp.initializer-test :refer [addon-test]]))

```
# Node Navigation

This module defines utility functions for navigating the decorated syntax
tree. Navigation includes finding definitions and references, finding
predicates that applied to a node and attributes (arguments, definition)
thereof, and more.

The functions in this module focus on providing this functionality while
minding edge cases, such as missing decorations, handing such cases
gracefully.

## Finding Matches

`get-matches` returns the contents of the `:matches` atom, if it exists, or
`{}` if it does not.
```clojure
(fact
 (let [node1 (with-meta [:foo]
               {:matches (atom {'some-predicate {:args [1 2 3]
                                                 :def [:foo-def]}})})
       node2 [:bar]]
   (get-matches node1) => {'some-predicate {:args [1 2 3]
                                            :def [:foo-def]}}
   (get-matches node2) => {}))

```
## Finding all Definitions

A node can have multiple definitions, each defining a different aspect,
recorded by a different predicate.

`get-defs` takes a node and returns a map containing the node's definitions
as values, with the corresponding predicates as keys.

Only non-`nil` definitions are returned.
```clojure
(fact
 (let [node (with-meta [:foo]
              {:matches (atom {'pred1 {:args [1 2 3]
                                       :def [:foo-def1]}
                               'pred2 {:args [1 2 3]
                                       :def [:foo-def2]}
                               'pred3 {:args [1 2 3]
                                       :def nil}})})]
   (get-defs node) => {'pred1 [:foo-def1]
                       'pred2 [:foo-def2]}))

```
If we do not care about the type of definition, `get-all-defs` returns the
definitions as a set.
```clojure
(fact
 (let [node (with-meta [:foo]
              {:matches (atom {'pred1 {:args [1 2 3]
                                       :def [:foo-def1]}
                               'pred2 {:args [1 2 3]
                                       :def [:foo-def2]}
                               'pred3 {:args [1 2 3]
                                       :def nil}})})]
   (get-all-defs node) => [[:foo-def1] [:foo-def2]]))

```
## Finding References

`get-refs` returns a collection of references to a given definition node.
```clojure
(fact
 (let [node1 (with-meta [:foo-def]
               {:refs (atom [[:foo 1] [:foo 2] [:foo 3]])})
       node2 [:bar-def]]
   (get-refs node1) => [[:foo 1] [:foo 2] [:foo 3]]
   (get-refs node2) => []))
```

