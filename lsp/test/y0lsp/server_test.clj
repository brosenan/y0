(ns y0lsp.server-test
  (:require [midje.sweet :refer [fact =>]]
            [y0lsp.server :refer :all]
            [lsp4clj.server :as server]
            [lsp4clj.lsp.requests :as lsp.requests]
            [clojure.core.async :as async]
            [lsp4clj.coercer :as coercer]))

;; # Universal Language Server

;; The goal of the `y0lsp` project is to build a universal language server. This
;; modules provides the base server, based on the
;; [lsp4clj](https://github.com/clojure-lsp/lsp4clj) project.

;; The server built here supports addons. The base server supports the base LSP
;; protocol, including initialization, requests and notifications. Addons can
;; then be added to support individual LSP features.

;; ## Handling Requests

;; Most LSP services are implemented by handling requests. The client sends a
;; request and the server handles it, returning a response.

;; To handle a request, the request type needs to be registered using the
;; `register-req` macro. Here is an example, registering the request
;; `testing/foo`.
(register-req "testing/foo"
              :testing-foo
              ::coercer/location)

;; `register-req` takes the request to register, its internal ID (a keyword) and
;; a [Spec](https://clojure.org/guides/spec) for the response. For the latter,
;; `lsp4clj`'s
;; [coercer](https://github.com/clojure-lsp/lsp4clj/blob/master/src/lsp4clj/coercer.clj)
;; library is a good resource.

;; Now we can implement a handler. The handler is placed in the `ctx` under
;; `:req-handlers`. Then `ctx` is passed to the server as it is initialized.
(fact
 (let [result {:uri "file:///foo.bar"
               :range {:start {:line 0 :character 12}
                       :end {:line 2 :character 0}}}
       ctx {:req-handlers {:testing-foo (constantly result)}}
       input-ch (async/chan 3)
       output-ch (async/chan 3)
       server (server/chan-server {:output-ch output-ch
                                   :input-ch input-ch})]
   (async/put! input-ch (lsp.requests/request 1 "testing/foo" {}))
   (server/start server ctx)
   (-> output-ch async/<!! :result) => result
   (server/shutdown server)))

;; The result must conform to the spec given to the `register-req` macro.
(fact
 (let [result {;; No :uri
               :range {:start {:line 0 :character 12}
                       :end {:line 2 :character 0}}}
       err-atom (atom nil)
       ctx {:req-handlers {:testing-foo (constantly result)}
            :err-atom err-atom}
       input-ch (async/chan 3)
       output-ch (async/chan 3)
       server (server/chan-server {:output-ch output-ch
                                   :input-ch input-ch})]
   (async/put! input-ch (lsp.requests/request 1 "testing/foo" {}))
   (server/start server ctx)
   (-> output-ch async/<!! :result) => nil
   (-> @err-atom :message) =>
   "Request output does not conform to the spec"
   (server/shutdown server)))

;; In the above example, we add `:err-atom` to the context as a way to get the
;; error object out of the server. In real life, this error will be logged.

;; The handler function takes two parameters: the server context (`ctx`) and the
;; request. It returns the response.

;; In the following example we implement a handler function that takes its
;; response from both the request and the context. It takes the location from
;; the request but overrides the `:uri` from the context.
(fact
 (let [request {:uri "file:///foo.bar"
                :range {:start {:line 0 :character 12}
                        :end {:line 2 :character 0}}}
       ctx {:req-handlers {:testing-foo (fn [{:keys [uri-override] :as _ctx} req]
                                          (-> req
                                              (assoc :uri uri-override)))}
            :uri-override "file:///bar.baz"}
       input-ch (async/chan 3)
       output-ch (async/chan 3)
       server (server/chan-server {:output-ch output-ch
                                   :input-ch input-ch})]
   (async/put! input-ch (lsp.requests/request 1 "testing/foo" request))
   (server/start server ctx)
   (-> output-ch async/<!! :result) => {:uri "file:///bar.baz"
                                        :range {:start {:line 0 :character 12}
                                                :end {:line 2 :character 0}}}
   (server/shutdown server)))
