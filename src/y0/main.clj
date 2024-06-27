(ns y0.main
  (:require [clojure.term.colors :refer [green red]]
            [clojure.tools.cli :refer [parse-opts]]
            [y0.builtins :refer [add-builtins]]
            [y0.explanation :refer [code-location explanation-to-str]]
            [y0.modules :refer [load-with-dependencies]]
            [y0.rules :refer [apply-statements]])
  (:gen-class))

(def cli-options
  [["-p" "--path PATH" "Add to Y0_PATH"
    :multi true
    :default []
    :update-fn conj]
   ["-h" "--help"]])

(defn- main [modules path]
  (let [[statements _] (load-with-dependencies modules path)
        ps (add-builtins {})
        status (apply-statements statements ps {})]
    (if (:err status)
      (let [explanation (:err status)
            message (explanation-to-str explanation)
            location (code-location explanation)]
        (binding [*out* *err*]
          (println (str (:path location) ":" (:row location) ": " (red "Error") ":") message))
        (System/exit 1))
      (println (green "Success")))))

(defn -main [& args]
  (let [cli (parse-opts args cli-options)
        {:keys [path help]} (:options cli)
        modlues (:arguments cli)]
    (cond
      help (println (:summary cli))
      :else (main modlues path))))
