(ns y0lsp.addon-utils-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addon-utils :refer :all]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.location-utils :refer [node-at-text-doc-pos]]
   [y0lsp.server :refer [register-notification register-req]]
   [y0lsp.workspace :refer [add-module eval-with-deps]]))

;; # Addon Utils

;; This module contains helper functions for creating addons.

;; ## Addon Registration

;; Addons are registered using the `register-addon` function. It registers the
;; addon under the given name in a global map held within an atom.
(register-addon "my-addon" #(assoc % :foo :bar))
(fact
 (-> @addons (get "my-addon")) => fn?)

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

;; The workspace is held by an atom which allows it to be updated.

;; The `get-module` takes a context and a path and returns the module with that
;; path, if exists in the workspace.

;; The function `swap-ws!` allows such updates. It takes a context, a function
;; and arguments for the function and calls it on the workspace.

;; In the following example we [add a module](workspace.md#adding-a-module) and
;; [evaluate it](workspace.md#evaluation-and-caching) using `swap-ws!`. Then we
;; use `get-module` to fetch the module we created and check that the `:ps` is
;; not `nil`.
(register-req "test/bar" any?)
(fact
 (let [{:keys [send shutdown]}
       (addon-test
        ;; Add and evaluate a module
        (add-req-handler "test/foo"
                         (fn [ctx {:keys [path text]}]
                           (swap-ws! ctx add-module {:path path :text text})
                           (swap-ws! ctx eval-with-deps path)
                           {}))
        ;; Retrieve a module's `:ps`
        (add-req-handler "test/bar"
                         (fn [ctx {:keys [path]}]
                           (-> (get-module ctx path) :cache :ps))))]
   (send "test/foo" {:path "/x.c0" :text "void foo() {}"}) => {}
   (send "test/bar" {:path "/x.c0"}) => #(not (nil? %))
   (shutdown)))

;; ### Accessing the Language Stylesheet

;; The [language stylesheet](language_stylesheet.md) provides a translation
;; layer, translating the language of $y_0$ predicates to the language of the
;; LSP.

;; The `:lang-map` holds, for each language, a compiled stylesheet under the key
;; `:lss`. The function `lss-for-node` takes a context and a parse-tree node and
;; returns the `:lss` corresponding to the language the node's containing module
;; is using.

;; In the following example we install one addon that updates the stylesheet of
;; laungauges `y1` and `c0`, and a second addon which evaluates attribute `:foo`
;; in the given module. We create a `c0` module and evaluate it on some node.
(fact
 (let [{:keys [add-module-with-pos send shutdown]}
       (addon-test
        #(update-in % [:config "y1"] assoc :stylesheet [{:foo 42}])
        #(update-in % [:config "c0"] assoc :stylesheet [{:foo :bar}])
        (add-req-handler "test/foo"
                         (fn [ctx req]
                           (let [node (node-at-text-doc-pos ctx req)
                                 lss (lss-for-node ctx node)]
                             (lss :foo)))))
       text-pos (add-module-with-pos "/path/to/x.c0" "void $foo() {}")]
   (send "test/foo" text-pos) => :bar
   (shutdown)))