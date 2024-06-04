(ns y0.main
  (:require [y0.core :refer []]
            [y0.modules :refer [load-with-dependencies]]
            [y0.rules :refer []]
            [y0.status :refer []]
            [y0.testing :refer []]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-p" "--path PATH" "Add to Y0_PATH"
    :multi true
    :default []
    :update-fn conj]
   ["-h" "--help"]])

(defn- main [file path]
  (let [statements (load-with-dependencies file path)]
    (println statements)))

(defn -main [& args]
  (let [cli (parse-opts args cli-options)
        {:keys [path help]} (:options cli)
        [file & files] (:arguments cli)]
    (cond
      help (println (:summary cli))
      (seq? files) (println "Only one positional argument expected. Extra:" files)
      :else (main file path))))
