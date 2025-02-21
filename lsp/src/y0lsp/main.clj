(ns y0lsp.main 
  (:gen-class)
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [edamame.impl.parser :refer [parse-string-all]]
   [lsp4clj.io-server :as io-server]
   [y0.config :refer [cwd]]
   [y0lsp.addon-utils :refer [addons]]
   [y0lsp.all-addons]
   [y0lsp.initializer :refer [initialize-context start]]))

(defn- all-addons []
  (for [[name addon] @addons]
    (with-meta addon {:addon name})))

(defn- log [& msgs]
  (.println java.lang.System/err (str ">> " (str/join " " msgs))))

(defn- main [dir]
  (let [lang-conf-file (io/file dir "lang-conf.edn")
        lang-conf (-> lang-conf-file slurp (parse-string-all {}) first)
        y0-path [dir]
        addons (all-addons)
        ctx (initialize-context lang-conf y0-path addons)
        server (io-server/stdio-server)
        done (start ctx server)]
    (log "Server started")
    (async/go-loop []
      (when-let [[level & args] (async/<! (:log-ch server))]
        (apply log level args)
        (recur)))
    @done))

(defn -main
  ([]
   (-main (cwd)))
  ([dir & args]
   (when (seq args)
     (.println java.lang.System/err
               (str "Unexpected command line arguments: " args))
     (java.lang.System/exit 1))
   (main (io/file dir))))