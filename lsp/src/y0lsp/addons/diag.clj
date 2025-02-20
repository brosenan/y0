(ns y0lsp.addons.diag
  (:require
   [y0.explanation :refer [code-location explanation-to-str]]
   [y0lsp.location-utils :refer [to-lsp-location uri-to-path]]
   [y0lsp.addon-utils :refer [add-notification-handler register-addon
                              get-module]]))

(defn explanation-to-diagnostic [expl]
  (let [{:keys [range]} (-> expl code-location to-lsp-location)]
    {:range range
     :severity 1
     :source "y0lsp"
     :message (explanation-to-str expl)}))

(register-addon "diag"
                (->> (fn [{:keys [notify] :as ctx} {:keys [uri]}]
                       (let [{:keys [semantic-errs]}
                             (get-module ctx (uri-to-path uri))
                             diagnostics (->> @semantic-errs
                                              (map :err)
                                              (map explanation-to-diagnostic))]
                         (notify "textDocument/publishDiagnostics"
                                 {:uri uri
                                  :diagnostics diagnostics})))
                     (add-notification-handler "y0lsp/moduleEvaluated")))