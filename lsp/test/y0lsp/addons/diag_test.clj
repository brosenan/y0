(ns y0lsp.addons.diag-test
  (:require
   [clojure.java.io :as io]
   [midje.sweet :refer [fact provided =>]]
   [y0.resolvers :refer [exists?]]
   [y0.status :refer [unwrap-status]]
   [y0lsp.addon-utils :refer [add-req-handler]]
   [y0lsp.addons.diag :refer :all]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.server :refer [register-req]]))

;; # Diagnostics

;; [Diagnostic
;; notifications](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics)
;; are the LSP's way of having the server show semantic errors and warnings to
;; the user. They are initiated by the server, and should be provided for any
;; change in the state of an open module.

;; The `diag` addon is responsible for providing diagnostic notifications. It
;; listens to the internal `y0lsp/moduleEvaluated` notification, indicating that
;; a module has a new evaluation state, and sends out a
;; `textDocument/publishDiagnostics` notification, containing
;; [`PublishDiagnosticsParams`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#publishDiagnosticsParams).

;; 