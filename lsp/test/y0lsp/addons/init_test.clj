(ns y0lsp.addons.init-test
  (:require
   [midje.sweet :refer [fact]]
   [y0lsp.addons.init :refer :all]))

;; # Initialization Addon

;; This core addon is responsible for handling the [`initialize`
;; request](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initialize).
;; It is responsible for exchanging capabilities between the server and the
;; client and for sending an internal notification to other addons, containing
;; the initialization message in full.

;; ## Capabilities Exchange

;; The handler adds a `:client-capabilities` atom to the context and populates
;; it with the `capabilities` received in the `initialize` request.

;; In the response, it sends capabilities that it reads from
;; `:server-capabilities` in the server context.

;; In the following example we build a 