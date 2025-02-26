```clojure
(ns y0lsp.addons.hover-test
  (:require
   [midje.sweet :refer [fact =>]]
   [y0lsp.addons.hover :refer :all]
   [y0lsp.addons.init]
   [y0lsp.initializer-test :refer [addon-test]]))

```
# Hover

A LSP [Hover
request](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover)
is sent from the client to the server, typically in order to provide a
tooltip on the code artifact under the mouse.

The request consists of a text document position, and the response contains a
string, which is either plain-text or markdown, depending on a [client
capability](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#markupContent).

The `hover` addon adds support for the `textDocument/hover` request. It
formats the tooltip text using the [language
stylesheet](language_stylesheet.md), with the keys `:hover` and
`:hover-md` used for plain-text and Markdown hover text, respectively.

## Capabilites

`hover` sets `:hover-provider` to `true` in the server capabilities.
```clojure
(fact
 (let [{:keys [send shutdown]} (addon-test "init" "hover")
       init-resp (send "initialize" {})]
   (-> init-resp :capabilities :hover-provider) => true
   (shutdown)))

```
## Plain Text Hover

In the following example we define a test addon that sets the `:stylesheet`
attribute of `c0` to contain a `:hover` key for `.typeof`, specifying the
type of the expression at the given position. Then we create a module with a
variable definition and usage, and make a `textDocument/hover` request.
```clojure
(fact
 (let [{:keys [send add-module-with-pos shutdown]}
       (addon-test "hover"
                   #(update-in %
                               [:config "c0"]
                               assoc :stylesheet
                               '[{}
                                 .typeof {:hover (str (with-pred [typeof :type]
                                                        ["Type:" :type]))}]))
       pos (add-module-with-pos "/path/to/x.c0"
                                "void foo() { int64 a = 1; int64 b = $a; }")]
   (send "textDocument/hover" pos) => {:contents ["Type: [:int64_type]"]}
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

