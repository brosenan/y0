(ns y0lsp.addons.diag
  (:require
   [y0.explanation :refer [*stringify-expr* code-location explanation-to-str]]
   [y0lsp.addon-utils :refer [add-notification-handler bind-stringify-expr
                              get-module register-addon]]
   [y0lsp.location-utils :refer [to-lsp-location uri-to-path]]))

(defn- safe-to-lsp-location [loc]
  (if (nil? loc)
  {:range {:start {:line 0 :character 0}
           :end {:line 0 :character 0}}}
  (to-lsp-location loc)))

(defn explanation-to-diagnostic [expl]
  (let [{:keys [range]} (-> expl code-location safe-to-lsp-location)]
    {:range range
     :severity 1
     :source "y0lsp"
     :message (explanation-to-str expl)}))

(register-addon "diag"
                (->> (fn [{:keys [notify] :as ctx} {:keys [uri]}]
                       (let [{:keys [semantic-errs err]}
                             (get-module ctx (uri-to-path uri))
                             diagnostics (if (nil? err)
                                           (->> @semantic-errs
                                                (map :err)
                                                (map explanation-to-diagnostic)
                                                vec)
                                           [(-> err explanation-to-diagnostic)])]
                         (notify "textDocument/publishDiagnostics"
                                 {:uri uri
                                  :diagnostics diagnostics})))
                     bind-stringify-expr
                     (add-notification-handler "y0lsp/moduleEvaluated")))