(ns y0.main
  (:require [clojure.term.colors :refer [green red blue]]
            [clojure.tools.cli :refer [parse-opts]]
            [y0.builtins :refer [add-builtins]]
            [y0.explanation :refer [code-location explanation-to-str all-unique-locations explanation-expr-to-str]]
            [y0.modules :refer [load-with-dependencies]]
            [y0.rules :refer [apply-statements]])
  (:gen-class))

(def cli-options
  [["-p" "--path PATH" "Add to Y0_PATH"
    :multi true
    :default []
    :update-fn conj]
   ["-h" "--help"]])

(defn render-location [location]
  (let [start (:start location)
        row (quot start 1000000)]
    (str (:path location) ":" row ": ")))

(defn- main [modules path]
  (let [[statements _] (load-with-dependencies modules path)
        ps (add-builtins {})
        status (apply-statements statements ps {})]
    (if (:err status)
      (let [explanation (:err status)
            message (explanation-to-str explanation)
            location (code-location explanation)]
        (binding [*out* *err*]
          (println (str (render-location location) (red "Error") ":") message)
          (vec (for [[term loc] (all-unique-locations explanation)]
                 (println (str (render-location loc) (blue "Note") ":") (explanation-expr-to-str term 10)))))
        (System/exit 1))
      (println (green "Success")))))

(defn -main [& args]
  (let [cli (parse-opts args cli-options)
        {:keys [path help]} (:options cli)
        modlues (:arguments cli)]
    (cond
      help (println (:summary cli))
      :else (main modlues path))))
