```clojure
(ns y0lsp.server-test
  (:require [midje.sweet :refer [fact =>]]
            [y0lsp.server :refer :all]
            [lsp4clj.server :as server]
            [lsp4clj.lsp.requests :as lsp.requests]
            [clojure.core.async :as async]
            [lsp4clj.coercer :as coercer]))

```
# Universal Language Server

The goal of the `y0lsp` project is to build a universal language server. This
modules provides the base server, based on the
[lsp4clj](https://github.com/clojure-lsp/lsp4clj) project.

The server built here supports addons. The base server supports the base LSP
protocol, including initialization, requests and notifications. Addons can
then be added to support individual LSP features.

## Testing Helpers

Before we dive into the server functionality, let us first introduce some
testing helpers that will make testing a server easier.

The function `test-server` takes a context and creates a `lsp4clj`
`chan-server`. It returns a function that allows a test to interact with the
server and to shut it down.
```clojure
(defn- test-server [ctx]
  (let [input-ch (async/chan 3)
        output-ch (async/chan 3)
        server (server/chan-server {:output-ch output-ch
                                    :input-ch input-ch})]
    (server/start server ctx)
    {:send-req (fn [msg req]
                 (async/put! input-ch
                             (lsp.requests/request 1 msg req))
                 (-> output-ch async/<!! :result))
     :notify (fn [msg notif]
               (async/put! input-ch
                           (lsp.requests/notification msg notif)))
     :shutdown (fn [] (server/shutdown server))}))

```
## Initialization

An LSP connection starts with an [initialization
handshake](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize)
in which the client and server exchange
[capabilities](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#capabilities).

The `y0lsp` handles the initialization request as follows:
* Client capabilities are stored in the server context, by updating the atom
  `:client-capabilities`.
* Server capabilities are provided from the server context, as stored under
  `:server-capabilities`.

In the following example we build a context containing `:server-capabilities`
and an empty atom for the `:client-capabilities`. Then we initialize a server
and send it an initialization request with some client capabilities.
Eventually, we check that the response contains our server capabilities and
that the `:client-capabilities` are updated from the request.
```clojure
(fact
 (let [ctx {:server-capabilities {:workspace {:apply-edit true}}
            :client-capabilities (atom nil)}
       {:keys [send-req shutdown]} (test-server ctx)]
   (send-req "initialize" {:client-capabilities
                           {:client :capabilities}}) =>
   {:server-capabilities {:workspace {:apply-edit true}}
    :server-info {:name "y0lsp"}}
   (-> ctx :client-capabilities deref) => {:client :capabilities}
   (shutdown)))

```
## Handling Requests

Most LSP services are implemented by handling requests. The client sends a
request and the server handles it, returning a response.

To handle a request, the request type needs to be registered using the
`register-req` macro. Here is an example, registering the request
`testing/foo`.
```clojure
(register-req "testing/foo"
              :testing-foo
              ::coercer/location)

```
`register-req` takes the request to register, its internal ID (a keyword) and
a [Spec](https://clojure.org/guides/spec) for the response. For the latter,
`lsp4clj`'s
[coercer](https://github.com/clojure-lsp/lsp4clj/blob/master/src/lsp4clj/coercer.clj)
library is a good resource.

Now we can implement a handler. The handler is placed in the `ctx` under
`:req-handlers`. Then `ctx` is passed to the server as it is initialized.
```clojure
(fact
 (let [result {:uri "file:///foo.bar"
               :range {:start {:line 0 :character 12}
                       :end {:line 2 :character 0}}}
       ctx {:req-handlers {:testing-foo (constantly result)}}
       {:keys [send-req shutdown]} (test-server ctx)]
   (send-req "testing/foo" {}) => result
   (shutdown)))

```
The result must conform to the spec given to the `register-req` macro.
```clojure
(fact
 (let [result {;; No :uri
               :range {:start {:line 0 :character 12}
                       :end {:line 2 :character 0}}}
       err-atom (atom nil)
       ctx {:req-handlers {:testing-foo (constantly result)}
            :err-atom err-atom}
       {:keys [send-req shutdown]} (test-server ctx)]
   (send-req "testing/foo" {}) => nil
   (-> @err-atom :message) =>
   "Request output does not conform to the spec"
   (shutdown)))

```
In the above example, we add `:err-atom` to the context as a way to get the
error object out of the server. In real life, this error will be logged.

The handler function takes two parameters: the server context (`ctx`) and the
request. It returns the response.

In the following example we implement a handler function that takes its
response from both the request and the context. It takes the location from
the request but overrides the `:uri` from the context.
```clojure
(fact
 (let [request {:uri "file:///foo.bar"
                :range {:start {:line 0 :character 12}
                        :end {:line 2 :character 0}}}
       ctx {:req-handlers {:testing-foo (fn [{:keys [uri-override] :as _ctx} req]
                                          (-> req
                                              (assoc :uri uri-override)))}
            :uri-override "file:///bar.baz"}
       {:keys [send-req shutdown]} (test-server ctx)]
   (send-req "testing/foo" request) => {:uri "file:///bar.baz"
                                        :range {:start {:line 0 :character 12}
                                                :end {:line 2 :character 0}}}
   (shutdown)))

```
## Handling Notifications

As part of the protocol, the client can send notifications to the server.

To handle a notification, the notification needs to be registered using the
`register-notification` macro, assigning a keyword to it.
```clojure
(register-notification "test/didFoo" :test-did-foo)

```
Then, in the context, handlers for this notifications are stored under
`:notification-handlers` and the keyword.

In the following example we create a context that contains two handlers for
the `:test-did-foo` notification, each updating a different atom with the
notification contents. We also add these atoms to the context. Then we send
the notification and check that its contents is indeed stored in these atoms.
```clojure
(fact
 (let [ctx {:notification-handlers
            {:test-did-foo [(fn [ctx notif]
                              (reset! (:notify1 ctx) notif))
                            (fn [ctx notif]
                              (reset! (:notify2 ctx) notif))]}
            :notify1 (atom nil)
            :notify2 (atom nil)}
       {:keys [notify shutdown]} (test-server ctx)]
   (notify "test/didFoo" {:foo :bar})
   (shutdown)
   (-> ctx :notify1 deref) => {:foo :bar}
   (-> ctx :notify2 deref) => {:foo :bar}))
```

