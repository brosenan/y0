```clojure
(ns y0lsp.addons.diag-test
  (:require
   [midje.sweet :refer [fact =>]]
   [y0lsp.addons.diag :refer :all]
   [y0lsp.addons.init]
   [y0lsp.addons.docsync]
   [y0lsp.initializer-test :refer [addon-test]]))

```
# Diagnostics

[Diagnostic
notifications](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics)
are the LSP's way of having the server show semantic errors and warnings to
the user. They are initiated by the server, and should be provided for any
change in the state of an open module.

The `diag` addon is responsible for providing diagnostic notifications. It
listens to the internal `y0lsp/moduleEvaluated` notification, indicating that
a module has a new evaluation state, and sends out a
`textDocument/publishDiagnostics` notification, containing
[`PublishDiagnosticsParams`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#publishDiagnosticsParams).

## Explanation to Diagnostic

A
[Diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#diagnostic)
is the LSP representation for errors and warnings. Here we only refer to
errors. [Explanations](../../doc/explanation.md) are the way $y_0$ semantic
errors are represented.

`explanation-to-diagnostic` takes an explanation and outputs a diagnostic.

In the following example we create an explanation with a known location and
translate it into a diagnostic.
```clojure
(fact 
 (let [expl ["This is an"
             (with-meta 'explanation {:path "/z.y0" :start 1000001 :end 2000003})]]
   (explanation-to-diagnostic expl) =>
   {:range {:start {:line 0 :character 0}
            :end {:line 1 :character 2}}
    :severity 1
    :source "y0lsp"
    :message "This is an explanation"}))

```
## The `diag` Addon

In the following example we demonstrate the `diag` addon. We define an
environment with `diag` and `docsync`. We send a `textDocument/didOpen` for a
`c0` file that does not have errors and see that we get a diagnostic
notification with no diagnostics.

Then we update the module to contain an error and see that we get a
diagnostic for it.
```clojure
(fact
 (let [ret (atom nil)
       {:keys [notify on-notification shutdown]} (addon-test "docsync" "diag")]
   (on-notification "textDocument/publishDiagnostics" (fn [params]
                                                        (reset! ret params)))
   (notify "textDocument/didOpen" {:text-document
                                   {:uri "file:///path/to/foo.c0"
                                    :text "void foo() {}"}})
   @ret => {:uri "file:///path/to/foo.c0"
            :diagnostics []}
   
   
   (notify "textDocument/didChange" {:text-document
                                     {:uri "file:///path/to/foo.c0"}
                                     :content-changes {:text "void foo() { bar(); }"}})
   @ret => {:uri "file:///path/to/foo.c0"
            :diagnostics [{:range {:start {:line 0 :character 12}
                                  :end {:line 0 :character 16}}
                          :severity 1
                          :source "y0lsp"
                          :message "Call to undefined function bar in [:expr_stmt [:expr ...]]"}]}
   (shutdown)))
  
  
```
If an error does not have location information, `:start` and `:end` are set
to (0,0).
```clojure
(fact
 (let [ret (atom nil)
       {:keys [notify on-notification shutdown]} (addon-test "docsync" "diag")]
   (on-notification "textDocument/publishDiagnostics" (fn [params]
                                                        (reset! ret params)))
   (notify "textDocument/didOpen" {:text-document
                                   {:uri "file:///path/to/foo.c0"
                                    :text "This does not parse"}})
   @ret => {:uri "file:///path/to/foo.c0"
            :diagnostics [{:range {:start {:line 0 :character 0}
                                   :end {:line 0 :character 0}}
                           :severity 1
                           :source "y0lsp"
                           :message ""}]}
   (shutdown)))
```

