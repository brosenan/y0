(ns y0lsp.addons.ws-folders
  (:require
   [y0lsp.addon-utils :refer [add-notification-handler register-addon]]
   [y0lsp.location-utils :refer [uri-to-path]]))

(let [folders (atom ["."])]
  (register-addon "ws-folders"
                  #(update-in % [:config-spec :path-prefixes]
                              assoc :default {:func (constantly (fn []
                                                                  @folders))
                                              :args []})
                  (->> (fn [ctx {:keys [workspace-folders]}]
                         (->> workspace-folders
                              (map :uri)
                              (map uri-to-path)
                              (reset! folders)))
                       (add-notification-handler "y0lsp/initialized"))))