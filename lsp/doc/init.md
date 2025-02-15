```clojure
(ns y0lsp.addons.init-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addon-utils :refer [add-notification-handler add-req-handler
                              merge-server-capabilities]]
   [y0lsp.addons.init :refer :all]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.server :refer [register-req]]))

```
# Initialization Addon

This core addon is responsible for handling the [`initialize`
request](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize).
It is responsible for exchanging capabilities between the server and the
client and for sending an internal notification to other addons, containing
the initialization message in full.

## Capabilities Exchange

The handler adds a `:client-capabilities` atom to the context and populates
it with the `capabilities` received in the `initialize` request.

In the response, it sends capabilities that it reads from
`:server-capabilities` in the server context.

In the following example we use the `init` addon alongside an addon that
defines some server capability and fetches a value from the client
capabilities.
```clojure
(register-req "test/foo" any?)
(fact
 (let [{:keys [send shutdown]}
       (addon-test "init"
                   (->> (fn [{:keys [client-capabilities]} _req]
                          {:res (:foo @client-capabilities)})
                        (add-req-handler "test/foo"))
                   (merge-server-capabilities
                    {:this {:server {:does :something}}}))]
   (send "initialize" {:capabilities {:foo 42}}) =>
   {:capabilities {:this {:server {:does :something}}}
    :server-info {:name "y0lsp" :version y0lsp.server/version}}
   (send "test/foo" {}) => {:res 42}
   (shutdown)))

```
## Sending a Notification

In addition to exchanging capabilities, the `init` addon also sends an
internal notification: `y0lps/initialized`, containing the full `initialize`
request. This is intended so that other addons can receive information from
the client other than the client capabilities.

In the following example we create an addon that listens to the
`y0lsp/initialized` notification and updates an atom with its contents. Then
we send an `initialize` request and see that the atom is updated.
```clojure
(fact
 (let [x (atom nil)
       {:keys [send shutdown]}
       (addon-test "init"
                   (->> (fn [_ctx params]
                          (reset! x params))
                        (add-notification-handler "y0lsp/initialized")))]
   (send "initialize" {:capabilities {:foo 42}
                       :something :else}) => map?
   @x => {:capabilities {:foo 42}
          :something :else}
   (shutdown)))
```

