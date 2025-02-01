(ns y0.main
  (:require
   [clojure.java.io :as io]
   [clojure.term.colors :refer [blue green red]]
   [clojure.tools.cli :refer [parse-opts]]
   [edamame.core :as e :refer [parse-string-all]]
   [y0.builtins :refer [add-builtins]]
   [y0.config :refer [*y0-path* language-map-from-config]]
   [y0.explanation :refer [all-unique-locations code-location
                           explanation-to-str]]
   [y0.polyglot-loader :refer [eval-mstore load-with-deps]]
   [y0.rules :refer [*skip-recoverable-assertions* apply-statements]]
   [y0.spec-analyzer :refer [process-lang-spec]]
   [y0.status :refer [let-s ok]])
  (:gen-class))

(def cli-options
  [["-p" "--path PATH" "Add to Y0_PATH"
    :multi true
    :default []
    :update-fn conj]
   ["-c" "--language-config CONFIG_PATH" "Path to the language configuration file"
    :default {}
    :parse-fn #(-> % slurp parse-string-all first)]
   ["-l" "--language LANG" "The language in which the given module names are written"
    :default "y0"
    :parse-fn identity]
   ["-s" "--language-spec" "If this is set, then the positional arguments are expected to be paths to markdown files, to be evaluated as language-specs"]
   ["-u" "--update-language-spec" "Same as -s, but will also update the markdown files with current statuses"]
   ["-h" "--help"]])

(defn render-location [location]
  (if (nil? location)
    ""
    (let [start (:start location)
          row (quot start 1000000)]
      (str (:path location) ":" row ": "))))

(defn- print-error [status]
  (let [explanation (:err status)
        message (explanation-to-str explanation)
        location (code-location explanation)]
    (binding [*out* *err*]
      (println (str (render-location location) (red "Error") ":") message)
      (vec (for [[term loc] (all-unique-locations explanation)]
             (println (str (render-location loc) (blue "Note") ":") (pr-str term)))))))

(defn- evaluate-statements [ps statements is-main]
  (binding [*skip-recoverable-assertions* (not is-main)]
    (apply-statements statements ps {})))

(defn- eval-modules [rpaths lang-map]
  (let [paths (for [rpath rpaths]
                (let [f (java.io.File. rpath)]
                  {:path (-> f .getAbsolutePath)}))
        status (let-s [mstore (load-with-deps paths lang-map)
                       ps (ok (add-builtins {}))]
                      (eval-mstore mstore evaluate-statements ps))]
    (if (:err status)
      (do
        (print-error status)
        (System/exit 1))
      (println (green "Success")))))

(defn- process-language-specs [files lang lang-map update?]
  (if (empty? files)
    nil
    (let [[file & files] files
          state (with-open [r (io/reader file)]
                  (let [lines (line-seq r)
                        state {:state :init
                               :lang lang
                               :langmap lang-map
                               :path file
                               :generate update?}]
                    (println (-> state :lagmap (get "c0")))
                    (process-lang-spec state lines)))]
      (doseq [err (:errors state)]
        (print-error {:err err}))
      (cond
        (:errors state) (println (-> state :errors count) (red "Failed")
                                 (if (:success state)
                                   (str "but " (:success state) " succeeded")
                                   ""))
        (:success state) (println (:success state) (green "Succeeded"))
        :else (println (blue "No tests ran")))
      (when update?
        (with-open [w (io/writer file)]
          (doseq [line (:generated state)]
            (.write w (str line (System/lineSeparator))))))
      (when (and (not update?)
                 (:errors state))
        (System/exit 1))
      (recur files lang lang-map update?))))

(defn- main [rpaths {:keys [path language language-config language-spec update-language-spec]}]
  (let [lang-map (binding [*y0-path* path]
                   (language-map-from-config language-config))]
    (cond
      update-language-spec (process-language-specs rpaths language lang-map true)
      language-spec (process-language-specs rpaths language lang-map false)
      :else (eval-modules rpaths lang-map))))

(defn -main [& args]
  (let [cli (parse-opts args cli-options)]
    (if (:errors cli)
      (binding [*out* *err*]
        (vec (for [err (:errors cli)]
               (println (str (red "Error") ":") err)))
        (System/exit 1))
      (let [{:keys [help] :as opts} (:options cli)
            rpaths (:arguments cli)]
        (cond
          help (println (:summary cli))
          :else (main rpaths opts))))))
