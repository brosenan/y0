```clojure
(ns y0lsp.addons.highlight-test
  (:require
   [midje.sweet :refer [fact =>]]
   [y0lsp.addons.highlight :refer :all]
   [y0lsp.all-addons]
   [y0lsp.initializer-test :refer [addon-test]]))

```
# Document Highlight

The LSP [Document Highlight
Request](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight)
is in intended to provide highlighting of related text, based on the current
cursor position. The LSP explicitly keeps the exact nature of the
highlighting vague to allow different servers to implement it differently for
different languages. In `y0lsp` we use it to highlight the _definition_ of
the node at the position, as well as all _other nodes that share the same
definition_.

## Capabilities

To enable highlighting requests, the server must set
`:document-highlight-provider` to `true` in the server capabilities.
```clojure
(fact
 (let [{:keys [send shutdown]} (addon-test "init" "highlight")
       {:keys [capabilities]} (send "initialize" {})]
   (:document-highlight-provider capabilities) => true
   (shutdown)))

```
## Highlighting

In the following example we define a variable and use it twice. Then we ask
for highlighting on one of the references. We expect to get the locations of
definition and both references returned.
```clojure
(fact
 (let [{:keys [send add-module-with-pos shutdown]} (addon-test "highlight")
       pos (add-module-with-pos "/path/to/a.c0"
                                "void foo() {
                                   int64 v1 = 1;
                                   int64 v2 = $v1;
                                   int64 v3 = v1;
                                 }")
       res (send "textDocument/documentHighlight" pos)]
   res => [{:range {:start {:line 0 :character 12}
                    :end {:line 1 :character 48}}} 
           {:range {:start {:line 3 :character 45}
                    :end {:line 3 :character 48}}}
           {:range {:start {:line 2 :character 45}
                    :end {:line 2 :character 48}}}]
   (shutdown)))

```
If the node does not have a definition, the request returns an empty list.
```clojure
(fact
 (let [{:keys [send add-module-with-pos shutdown]} (addon-test "highlight")
       pos (add-module-with-pos "/path/to/a.c0"
                                "void foo() {
                                   in$t64 v1 = 1;
                                   int64 v2 = v1;
                                   int64 v3 = v1;
                                 }")
       res (send "textDocument/documentHighlight" pos)]
   res => []
   (shutdown)))
```

