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

;; ## Explanation to Diagnostic

;; A
;; [Diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#diagnostic)
;; is the LSP representation for errors and warnings. Here we only refer to
;; errors. [Explanations](../../doc/explanation.md) are the way $y_0$ semantic
;; errors are represented.

;; `explanation-to-diagnostic` takes an explanation and outputs a diagnostic.

;; In the following example we create an explanation with a known location and
;; translate it into a diagnostic.
(fact 
 (let [expl ["This is an"
             (with-meta 'explanation {:path "/z.y0" :start 1000001 :end 2000003})]]
   (explanation-to-diagnostic expl) =>
   {:range {:start {:line 0 :character 0}
            :end {:line 1 :character 2}}
    :severity 1
    :source "y0lsp"
    :message "This is an explanation"}))
