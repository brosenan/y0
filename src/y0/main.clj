(ns y0.main
  (:require [clojure.term.colors :refer [green red blue]]
            [clojure.tools.cli :refer [parse-opts]]
            [y0.builtins :refer [add-builtins]]
            [y0.explanation :refer [code-location explanation-to-str all-unique-locations explanation-expr-to-str]]
            [y0.polyglot-loader :refer [load-with-deps eval-mstore]]
            [y0.edn-parser :refer [edn-parser root-module-symbols]]
            [y0.resolvers :refer [y0-resolver]]
            [y0.core :refer [y0-symbols]]
            [y0.status :refer [ok let-s]]
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
  (let [lang-map {"y0" {:parse (edn-parser
                                (root-module-symbols y0-symbols "y0.core")
                                "y0"
                                [])
                        :resolve (y0-resolver path)}}
        modules (for [module modules]
                  {:lang "y0"
                   :name module})
        status (let-s [mstore (load-with-deps modules lang-map)
                       ps (ok (add-builtins {}))]
                      (eval-mstore mstore #(apply-statements %2 %1 {}) ps))]
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
