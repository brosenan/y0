```clojure
(ns y0lsp.addons.docsync-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addons.docsync :refer :all]
   [y0lsp.addon-utils :refer [add-notification-handler add-req-handler
                              merge-server-capabilities]]
   [y0lsp.initializer-test :refer [addon-test]]))

```
# Document Synchronization

`docsync` is a core addon that adds server support for [text document
synchronization](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_synchronization).

This involves updating the [workspace](workspace.md) on changes to documents
or changes to their state. This includes when a document is opened
(`textDocument/didOpen`), is updated (`textDocument/didChange`) or closed
(`textDocument/didClose`).

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

