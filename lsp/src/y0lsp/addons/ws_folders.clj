(ns y0lsp.addons.ws-folders
  (:require
   [y0lsp.addon-utils :refer [add-notification-handler register-addon]]
   [y0lsp.location-utils :refer [uri-to-path]]))

(defn- append-if [elem list]
  (if (nil? elem)
    list
    (concat list [elem])))

(let [folders (atom ["."])]
  (register-addon "ws-folders"
                  #(update-in % [:config-spec :path-prefixes]
                              assoc :default {:func (constantly (fn []
                                                                  @folders))
                                              :args []})
                  (->> (fn [_ctx {:keys [workspace-folders root-uri]}]
                         (->> workspace-folders
                              (map :uri)
                              (append-if root-uri)
                              (map uri-to-path)
                              (reset! folders)))
                       (add-notification-handler "y0lsp/initialized"))))