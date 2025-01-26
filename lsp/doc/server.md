```clojure
(ns y0lsp.server-test
  (:require [midje.sweet :refer [fact =>]]
            [y0lsp.server :refer :all]
            [lsp4clj.server :as server]
            [lsp4clj.lsp.requests :as lsp.requests]
            [clojure.core.async :as async]))

```
# Universal Language Server

The goal of the `y0lsp` project is to build a universal language server. This
modules provides the base server, based on the
[lsp4clj](https://github.com/clojure-lsp/lsp4clj) project.

The server built here supports addons. The base server supports the base LSP
protocol, including initialization, requests and notifications. Addons can
then be added to support individual LSP features.

## Handling Requests

Most LSP services are implemented by handling requests. The client sends a
request and the server handles it, returning a response.

To handle a request, the request type needs to be registered in
[server.clj](../src/y0lsp/server.clj). Then, the context needs to contain a
handler under `:req-handlers`.
```clojure
(fact
 (let [result {:uri "file:///foo.bar"
               :range {:start {:line 0 :character 12}
                       :end {:line 2 :character 0}}}
       ctx {:req-handlers {:text-doc-declaration (constantly result)}}
       input-ch (async/chan 3)
       output-ch (async/chan 3)
       server (server/chan-server {:output-ch output-ch
                                   :input-ch input-ch})]
   (async/put! input-ch (lsp.requests/request 1 "textDocument/declaration" {}))
   (server/start server ctx)
   (-> output-ch async/<!! :result) => result
   (server/shutdown server)))

```
The result must conform to the spec given to the `register-req` macro.
```clojure
(fact
 (let [result {;; No :uri
               :range {:start {:line 0 :character 12}
                       :end {:line 2 :character 0}}}
       err-atom (atom nil)
       ctx {:req-handlers {:text-doc-declaration (constantly result)}
            :err-atom err-atom}
       input-ch (async/chan 3)
       output-ch (async/chan 3)
       server (server/chan-server {:output-ch output-ch
                                   :input-ch input-ch})]
   (async/put! input-ch (lsp.requests/request 1 "textDocument/declaration" {}))
   (server/start server ctx)
   (-> output-ch async/<!! :result) => nil
   (-> @err-atom :message) =>
   "Request output does not conform to the spec"
   (server/shutdown server)))

```
In the above example, we add `:err-atom` to the context as a way to get the
error object out of the server. In real life, this error will be logged.

