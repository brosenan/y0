(ns y0lsp.addons.diag
  (:require
   [y0.explanation :refer [code-location explanation-to-str *stringify-expr*]] 
   [y0lsp.location-utils :refer [to-lsp-location uri-to-path]]
   [y0lsp.addon-utils :refer [add-notification-handler register-addon
                              get-module]]))

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

(defn- expr-stringifier [langmap lang]
  (-> langmap
      (get lang)
      (get :stringify-expr *stringify-expr*)))

(register-addon "diag"
                (->> (fn [{:keys [notify lang-map] :as ctx} {:keys [uri]}]
                       (let [{:keys [semantic-errs lang]}
                             (get-module ctx (uri-to-path uri))
                             diagnostics (binding [*stringify-expr*
                                                   (expr-stringifier lang-map lang)]
                                           (->> @semantic-errs
                                                (map :err)
                                                (map explanation-to-diagnostic)
                                                vec))]
                         (notify "textDocument/publishDiagnostics"
                                 {:uri uri
                                  :diagnostics diagnostics})))
                     (add-notification-handler "y0lsp/moduleEvaluated")))