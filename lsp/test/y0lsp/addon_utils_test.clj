(ns y0lsp.addon-utils-test
  (:require
   [midje.sweet :refer [fact]]
   [clojure.java.io :as io]
   [y0lsp.addon-utils :refer :all]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.server :refer [register-notification register-req]]
   [y0lsp.workspace :refer [add-module eval-with-deps]]))

;; # Addon Utils

;; This module contains helper functions for creating addons.

;; ## Context Manipulators

;; In this section we describe functions that create functions that update the
;; server context. These functions can be composed to create an addon.

;; ### Handler Installation Functions

;; The `y0lsp` [server](server.md) allows for
;; [request](server.md#handling-requests) and
;; [notification](server.md#handling-notifications) handlers to be installed to
;; the server context.

;; `add-req-handler` takes a request name and a handler and returns an addon
;; function that installs it to the context.
(register-req "test/foo" any?)
(fact
 (let [my-handler (fn [{:keys [req-handlers]} req]
                    {:got req
                     :handlers req-handlers})
       {:keys [send shutdown]}
       (addon-test (add-req-handler "test/foo" my-handler))]
   (send "test/foo" {:foo :bar}) => {:got {:foo :bar}
                                     :handlers {"test/foo" my-handler}}
   (shutdown)))

;; `add-notification-handler` takes a notification name and handler and returns
;; a addon function that adds the handler as a handler for that notification.
(register-notification "test/didFoo")
(fact
 (let [x (atom nil)
       my-handler (fn [_ctx params]
                    (reset! x params))
       {:keys [notify shutdown]}
       (addon-test (add-notification-handler "test/didFoo" my-handler))]
   (notify "test/didFoo" {:foo :bar})
   @x => {:foo :bar}
   (shutdown)))

;; ### Capabilities

;; The key `:server-capabilities` in the server context collects the [Server
;; capabilities](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#serverCapabilities)
;; that will be sent to the client on initialization.

;; `merge-server-capabilities` takes a partial capabilities map and returns a
;; function that merges it to the `:server-capabilities` in the context.
(fact
 (let [f (comp (merge-server-capabilities {:foo {:bar :baz}
                                           :x 1})
               (merge-server-capabilities {:foo {:baz :bar}
                                           :x 2}))]
   (f {}) => {:server-capabilities {:foo {:bar :baz
                                          :baz :bar}
                                    :x 1}}))

;; ## Workspace Access Functions

;; When implementing request and notification handlers, we often need to access
;; the the workspace. This involves reading state and manipulation.

;; The workspace is held by an atom which allows it to be updated. The function
;; `swap-ws!` allows such updates. It takes a context, a function and arguments
;; for the function and calls it on the workspace.

;; In the following example we [add a module](workspace.md#adding-a-module) and
;; [evaluate it](workspace.md#evaluation-and-caching) using `swap-ws!`.
(register-req "test/bar" any?)
(fact
 (let [{:keys [send shutdown]}
       (addon-test
        (add-req-handler "test/foo"
                         (fn [ctx {:keys [path text]}]
                           (swap-ws! ctx add-module {:path path :text text})
                           (swap-ws! ctx eval-with-deps path)
                           {}))
        (add-req-handler "test/bar"
                         (fn [ctx {:keys [path]}]
                           (-> ctx :ws deref :ms (get path) :cache :ps))))]
   (send "test/foo" {:path "/x.c0" :text "void foo() {}"}) => {}
   (send "test/bar" {:path "/x.c0"}) => #(not (nil? %))
   (shutdown)))
