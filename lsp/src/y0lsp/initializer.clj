(ns y0lsp.initializer 
  (:require
   [y0.config :refer [keys-map language-map-from-config]]
   [y0.polyglot-loader :refer [load-module]]
   [y0.rules :refer [*error-target* *skip-recoverable-assertions*]]
   [y0.status :refer [ok?]]
   [y0lsp.language-stylesheet :refer [compile-stylesheet]]
   [y0lsp.tree-index :refer [index-nodes]]))

(defn to-language-map [conf-spec lang-config]
  (language-map-from-config lang-config
                            (-> conf-spec
                                (assoc :lss {:default {:func #(compile-stylesheet %)
                                                        :args [:stylesheet]}}))
                            (-> keys-map
                                (assoc :lss :lss))))

(defn- add-index [{:keys [statements] :as m}]
  (assoc m :index (index-nodes statements)))

(defn- replace-error [status m]
  (if (ok? status)
    (:ok status)
    (assoc m :err (:err status))))

(defn module-loader [lang-map]
  (fn [m]
    (-> m
        (load-module lang-map)
        (replace-error m)
        add-index
        (assoc :semantic-errs (atom nil)))))

(defn module-evaluator [apply-statements]
  (fn [ps {:keys [statements semantic-errs is-open] :as m}]
    (reset! semantic-errs nil)
    (let [status (binding [*error-target* semantic-errs
                           *skip-recoverable-assertions* (not is-open)]
                   (apply-statements statements ps {}))]
      (if (ok? status)
        (:ok status)
        (do
          (swap! semantic-errs conj (:err status))
          ps)))))