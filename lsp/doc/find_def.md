```clojure
(ns y0lsp.addons.find-def-test
  (:require
   [midje.sweet :refer [fact =>]]
   [y0lsp.addons.find-def :refer :all]
   [y0lsp.addons.init]
   [y0lsp.initializer-test :refer [addon-test]]))

```
# Find Definition

The LSP [find
definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition)
feature allows for the client to request the definition location of a code
artifact at a position pointed to by the client. This is typically used to
allow the editor to jump to the definition location of the identifier pointed
to by the curser or mouse.

The `find-def` addon adds support for the `textDocument/definition` request.

## Capabilites

`find-def` sets `:definition-provider` to `true` in the server capabilities.
```clojure
(fact
 (let [{:keys [send shutdown]} (addon-test "init" "find-def")
       init-resp (send "initialize" {})]
   (-> init-resp :capabilities :definition-provider) => true
   (shutdown)))

```
## Find Definition Request

In the following example we create a module containing two functions, one
using the other. We get the position of the function name in the function
call and request the definition.
```clojure
(fact
 (let [{:keys [send add-module-with-pos shutdown]} (addon-test "find-def")
       pos (add-module-with-pos "/path/to/x.c0"
                                "void foo() {} void bar() { $foo(); }")]
   (send "textDocument/definition" pos) => {:uri "file:///path/to/x.c0"
                                            :range {:start {:line 0
                                                            :character 0}
                                                    :end {:line 0
                                                          :character 13}}}
   (shutdown)))

```
If the artifact at the location does not have a definition, the request
returns `nil`.
```clojure
(fact
 (let [{:keys [send add-module-with-pos shutdown]} (addon-test "find-def")
       pos (add-module-with-pos "/path/to/x.c0"
                                "void foo() {} void bar() { foo($); }")]
   (send "textDocument/definition" pos) => nil
   (shutdown)))
```

