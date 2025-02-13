```clojure
(ns y0lsp.addons.init-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addon-utils :refer [add-req-handler merge-server-capabilities]]
   [y0lsp.addons.init :refer :all]
   [y0lsp.server :refer [register-req]]
   [y0lsp.initializer-test :refer [addon-test]]))

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
   (send "test/foo" {}) => {:res 42}))
```

