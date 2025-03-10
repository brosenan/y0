(ns y0lsp.initializer 
  (:require
   [lsp4clj.server :as server]
   [y0.builtins :refer [add-builtins]]
   [y0.config :refer [*y0-path* keys-map lang-config-spec
                      language-map-from-config]]
   [y0.polyglot-loader :refer [load-module]]
   [y0.rules :refer [*error-target* *skip-recoverable-assertions*
                     apply-statements]]
   [y0.status :refer [ok?]]
   [y0lsp.language-stylesheet :refer [compile-stylesheet]]
   [y0lsp.tree-index :refer [index-nodes]]
   [y0lsp.workspace :refer [new-workspace]]))

(defn- to-language-map [conf-spec lang-config]
  (language-map-from-config lang-config
                            (-> conf-spec
                                (assoc :lss {:default {:func #(compile-stylesheet %)
                                                        :args [:stylesheet]}}))
                            (-> keys-map
                                (assoc :lss :lss))))

(defn create-language-map [{:keys [config config-spec] :as ctx}]
  (-> ctx
      (assoc :lang-map (to-language-map config-spec config))))

(defn- add-index [{:keys [statements] :as m}]
  (assoc m :index (index-nodes statements)))

(defn- replace-error [status m]
  (if (ok? status)
    (:ok status)
    (assoc m :err (:err status))))

(defn module-loader [{:keys [lang-map]}]
  (fn [m]
    (-> m
        (load-module lang-map)
        (replace-error m)
        add-index
        (assoc :semantic-errs (atom nil)))))

(defn module-evaluator [apply-statements]
  (fn [ps {:keys [statements semantic-errs is-open]}]
    (reset! semantic-errs nil)
    (let [ps (if (nil? ps) (add-builtins {}) ps)
          status (binding [*error-target* semantic-errs
                           *skip-recoverable-assertions* (not is-open)]
                   (apply-statements statements ps {}))]
      (if (ok? status)
        (:ok status)
        (do
          (swap! semantic-errs conj (:err status))
          ps)))))

(defn create-workspace [{:keys [y0-path] :as ctx}]
  (let [ctx (binding [*y0-path* y0-path]
                   (-> ctx create-language-map))]
    (-> ctx
      (assoc :ws (atom (new-workspace (module-loader ctx)
                                      (module-evaluator apply-statements)))))))

(defn initialize-context [config y0-path addons]
  (let [ctx {:config config
             :config-spec lang-config-spec
             :y0-path y0-path}
        ctx (reduce #(%2 %1) ctx addons)]
       (create-workspace ctx)))

(defn start [ctx server]
  (server/start server 
                (-> ctx
                    (assoc :server server)
                    (assoc :notify (fn [name params]
                                     (server/send-notification server name params))))))