```clojure
(ns y0lsp.location-utils-test
  (:require
   [midje.sweet :refer [=> fact]]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.location-utils :refer :all]))

```
# Location Utilities

[Locations](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#location)
and
[positions](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#position)
are used in many LSP messages. This module provides utilities to convert
between the LSP representations of locations and positions and the
[ones](../..//doc/location_util.md) used by the $y_0$ implementation.

The key differences between the two representations are:

1. The LSP uses zero-based numbering for rows and columns while the $y_0$
   implementation uses one-based numbering.
2. LSP uses maps for positions while the $y_0$ implementation uses integers.
3. File paths are represented as URIs in the LSP, while $y_0$ uses absolute
   paths.

## Position Conversion

The function `from-lsp-pos` takes an [LSP
position](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#position)
and reutnrs a corresponding number used as position by $y_0$.
```clojure
(fact
 (from-lsp-pos {:line 3 :character 5}) => 4000006)

```
Conversely, the function `to-lsp-pos` takes a numeric position and returns
and LSP position.
```clojure
(fact
 (to-lsp-pos 4000006) => {:line 3 :character 5})

```
`from-lsp-text-doc-pos` converts a LSP
[TextDocumentPositionParams](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentPositionParams)
to a pair of an absolute path and a numeric position.
```clojure
(fact
 (from-lsp-text-doc-pos {:text-document {:uri "file:///path/to/module.c0"}
                         :position {:line 3
                                    :character 5}}) =>
 ["/path/to/module.c0" 4000006])

```
## Location Conversion

`to-lsp-location` takes a $y_0$ location map and returns a corresponding [LSP
location](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#location).
```clojure
(fact
 (to-lsp-location {:start 4000001
                   :end 4000006
                   :path "/path/to/module.c0"}) =>
 {:uri "file:///path/to/module.c0"
  :range {:start {:line 3 :character 0}
          :end {:line 3 :character 5}}})

```
## Node Fetching

To implement LSP services, we often need access to nodes in the parse-tree
that correspond to a given position.

The function `node-at-text-doc-pos` takes a server context and a
[TextDocumentPositionParams](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentPositionParams)
to an already-open module and returns the node that [best
matches](tree_index.md#positions-and-nodes) that position in the module.
```clojure
(fact
 (let [extract-node (fn [ctx req]
                      {:node (node-at-text-doc-pos ctx req)})
       {:keys [add-module-with-pos send shutdown]} (addon-test
                                               #(update % :req-handlers
                                                        assoc "test/foo"
                                                        extract-node))
       text-doc-pos (add-module-with-pos "/path/to/m.c0" "void $foo(){}")]
   (send "test/foo" text-doc-pos) => {:node (symbol "/path/to/m.c0" "foo")}
   (shutdown)))

```
## Node Location

The flip side of `node-at-text-doc-pos` is to take a parse-tree node and
translate its location to an LSP
[location](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#location).

`node-location` does this. It takes a node and returns a location.

In the following example we fetch a node at a given position (see [previous
section](#node-fetching)) and return its location.
```clojure
(fact
 (let [get-node-location (fn [ctx req]
                           (let [node (node-at-text-doc-pos ctx req)]
                             (node-location node)))
       {:keys [add-module-with-pos send shutdown]} (addon-test
                                                    #(update % :req-handlers
                                                             assoc "test/foo"
                                                             get-node-location))
       text-doc-pos (add-module-with-pos "/path/to/m.c0" "void $foo(){}")]
   (send "test/foo" text-doc-pos) => {:uri "file:///path/to/m.c0"
                                      :range {:start {:line 0 :character 4}
                                              :end {:line 0 :character 8}}}
   (shutdown)))
```

