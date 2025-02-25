```clojure
(ns y0lsp.addons.docsync-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addons.docsync :refer :all]
   [y0lsp.addon-utils :refer [add-notification-handler add-req-handler
                              merge-server-capabilities get-module]]
   [y0lsp.server :refer [register-req]]
   [y0lsp.location-utils :refer [uri-to-path]]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.addons.init]))

```
# Document Synchronization

`docsync` is a core addon that adds server support for [text document
synchronization](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_synchronization).

This involves updating the [workspace](workspace.md) when a document is
opened (`textDocument/didOpen`) ofupdated (`textDocument/didChange`). Other
changes (e.g., a document being closed) can be gracefully ignored.

## Server Capabilities

To tell the client to send notifications on opening,and closing of documents,
as well as for document updates, the addon needs to add keys to the
`:server-capabilities`.

In the following example we send an `initialize` request and check the
contents of `:text-document-sync` in the returned `:capabilities`, and see
that they request full synchronization for both opening, closing and changes
of documents.
```clojure
(fact
 (let [{:keys [send shutdown]} (addon-test "init" "docsync")]
   (-> (send "initialize" {})
       :capabilities
       :text-document-sync) => {:open-close true
                                :change 1}
   (shutdown)))

```
## Opening a Document

When a document has been opened, the client sends a `textDocument/didOpen`
notification with the document URI and text.

`docsync` handles this notification by adding and evaluating it.

We demonstrate this by creating a test addon that checks whether the module
at the given path contains a `:cache`, which is an indication of it being
evaluated. Then we send a `didOpen` notification and call the test addon to
see its effect.
```clojure
(register-req "test" any?)
(fact
 (let [{:keys [notify send shutdown]}
       (addon-test "docsync"
                   (->> (fn [ctx {:keys [path]}]
                          {:is-evaluated (-> (get-module ctx path)
                                             (contains? :cache))})
                        (add-req-handler "test")))]
   (notify "textDocument/didOpen"
           {:text-document {:uri "file:///path/to/mod.c0"
                            :text "void foo() {}"}})
   (send "test" {:path "/path/to/mod.c0"}) => {:is-evaluated true}
   (shutdown)))

```
$y_0$ supports two modes of evaluation, intended for open files, and
evaluation that skips assertions for dependencies.

In order for an open file to do do the former, `:is-open` [needs to be
set](initializer.md#error-handling).

In the following example, the test addon returns the value of `:is-open`.
```clojure
(fact
 (let [{:keys [notify send shutdown]}
       (addon-test "docsync"
                   (->> (fn [ctx {:keys [path]}]
                          {:is-open (-> (get-module ctx path)
                                        :is-open)})
                        (add-req-handler "test")))]
   (notify "textDocument/didOpen"
           {:text-document {:uri "file:///path/to/mod.c0"
                            :text "void foo() {}"}})
   (send "test" {:path "/path/to/mod.c0"}) => {:is-open true}
   (shutdown)))

```
After the module has been loaded and evaluated, the addon sends an internal
`y0lsp/moduleEvaluated` notification, containing the module's `:uri`.

We demonstrate this by creating a test addon which registers to this
notification, counts the number of `:semantic-errs` in the module and updates
an atom with this value.

Then we send a `textDocument/didOpen` notification with a module containing a
semantic error and see that it is being counted.
```clojure
(fact
 (let [res (atom nil)
       {:keys [notify shutdown]}
       (addon-test "docsync"
                   (->> (fn [ctx {:keys [uri]}]
                          (let [path (uri-to-path uri)]
                            (reset! res (-> (get-module ctx path)
                                            :semantic-errs deref count))))
                        (add-notification-handler "y0lsp/moduleEvaluated")))]
   (notify "textDocument/didOpen"
           {:text-document {:uri "file:///path/to/mod.c0"
                            :text "void foo() { bar(); }"}})
   @res => 1
   (shutdown)))

```
## Updating a Document

When a document is updated on disk, the client sends a
`textDocument/didChange` notification. `docsync` handles it by [updating the
module in the workspace](workspace.md#module-updates).

We demonstrate this by creating a test addon that returns the number of the
module's `:semantic-errs`. Then we send a `didOpen` notification on some
module, and then call the test addon to see that there are no errors. Then we
send a `didChange` notification on the same module, containing a semantic
error, and call the test addon again to see its effect.
```clojure
(fact
 (let [{:keys [notify send shutdown]}
       (addon-test "docsync"
                   (->> (fn [ctx {:keys [path]}]
                          {:num-errs (-> (get-module ctx path)
                                         :semantic-errs deref count)})
                        (add-req-handler "test")))]
   (notify "textDocument/didOpen"
           {:text-document {:uri "file:///path/to/mod.c0"
                            :text "void foo() {}"}})
   (send "test" {:path "/path/to/mod.c0"}) => {:num-errs 0}
   
   
   (notify "textDocument/didChange"
           {:text-document {:uri "file:///path/to/mod.c0"}
            :content-changes [{:text "void foo() { bar(); }"}]})
   (send "test" {:path "/path/to/mod.c0"}) => {:num-errs 1} 
   (shutdown)))

```
After an update is complete, the handler sends a `y0lsp/moduleEvaluated` with
the module's `:uri`.

We demonstrate this by repeating the previous example, but this time, instead
of defining a custom request handler to return the number of errors, we use
the notification to update an atom.
```clojure
(fact
 (let [res (atom nil)
       {:keys [notify shutdown]}
       (addon-test "docsync"
                   (->> (fn [ctx {:keys [uri]}]
                          (let [path (uri-to-path uri)]
                            (reset! res (-> (get-module ctx path)
                                            :semantic-errs deref count))))
                        (add-notification-handler "y0lsp/moduleEvaluated")))]
   (notify "textDocument/didOpen"
           {:text-document {:uri "file:///path/to/mod.c0"
                            :text "void foo() {}"}})
   @res => 0

   (notify "textDocument/didChange"
           {:text-document {:uri "file:///path/to/mod.c0"}
            :content-changes [{:text "void foo() { bar(); }"}]})
   @res => 1
   (shutdown)))
```

