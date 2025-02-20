(ns y0lsp.main 
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [edamame.impl.parser :refer [parse-string-all]]
   [y0.config :refer [cwd]]
   [y0lsp.addon-utils :refer [addons]]
   [y0lsp.initializer :refer [initialize-context]]))

(defn- all-addons []
  (vec (->> (io/resource "addons")
            io/file
            file-seq
            (filter #(str/ends-with? % ".clj"))
            (map #(.getName %))
            (map #(str/replace % ".clj" ""))
            (map #(str "y0lsp.addons." %))
            (map symbol)
            (map require)))
  (for [[name addon] @addons]
    (with-meta addon {:addon name})))

(defn- main [dir]
  (let [lang-conf-file (io/file dir "lang-conf.edn")
        lang-conf (-> lang-conf-file slurp (parse-string-all {}) first)
        y0-path [dir]
        addons (all-addons)
        ctx (initialize-context lang-conf y0-path addons)]
    (println ctx)))

(defn -main
  ([]
   (-main (cwd)))
  ([dir & args]
   (when (seq args)
     (.println java.lang.System/err
               (str "Unexpected command line arguments: " args))
     (java.lang.System/exit 1))
   (main (io/file dir))))