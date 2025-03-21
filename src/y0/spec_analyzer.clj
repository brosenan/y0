(ns y0.spec-analyzer
  (:require [clojure.string :refer [join]]
            [y0.builtins :refer [add-builtins]]
            [y0.explanation :refer [explanation-to-str *stringify-expr*]]
            [y0.polyglot-loader :refer [eval-mstore load-with-deps]]
            [y0.rules :refer [apply-statements]]
            [y0.status :refer [let-s ok]]
            [y0.term-utils :refer [postwalk-meta]]
            [y0.unify :refer [reify-term]]))

(defn find-transition [transes line]
  (loop [transes transes]
    (if (empty? transes)
      [{} nil]
      (let [[trans & transes] transes]
        (if (contains? trans :pattern)
          (let [matches (re-matches (:pattern trans) line)]
            (if (nil? matches)
              (recur transes)
              [(dissoc trans :pattern) matches]))
          [trans [line]])))))

(defn- apply-line-single-state [s state-spec v line]
  (let [[trans matches] (find-transition state-spec line)
        s (if (contains? trans :transition)
            (:transition trans)
            s)
        v (assoc v :state s)
        v (if (contains? trans :update-fn)
            ((:update-fn trans) v matches)
            v)]
    v))

(defn apply-line [sm v line]
  (let [s (:state v)
        state-spec (get sm s)
        v (if (contains? sm :any)
            (apply-line-single-state s (get sm :any) v line)
            v)]
    (apply-line-single-state s state-spec v line)))

(defn process-lines
  ([sm v lines]
   (if (empty? lines)
     v
     (let [[line & lines] lines
           v (apply-line sm v line)]
       (recur sm v lines)))))

(defn- update-if [m cond key f]
  (if cond
    (update m key f)
    m))

(defn- eval-code [{:keys [lang langmap current-block modules]}]
  (let [langmap (update langmap lang #(-> %
                                          (assoc :resolve 
                                                 (fn [name] (ok name)))
                                          (assoc :read 
                                                 (fn [name]
                                                   (->> (get modules name)
                                                        (join "\n"))))
                                          (assoc :match
                                                 (constantly true))))]
    (let-s [mstore (load-with-deps [{:lang lang
                                     :path "example"
                                     :text (join "\n" current-block)}]
                                   langmap)
            ps (ok (add-builtins {}))]
           (eval-mstore mstore (fn [ps statements _is-main]
                                 (apply-statements statements ps {})) ps))))

(defn convert-error-locations
  ([explanation spec-path line]
   (->> explanation
        reify-term
        (postwalk-meta (fn [{:keys [path start end] :as m}]
                         (if (= path "example")
                           {:start (+ start (* line 1000000))
                            :end (+ end (* line 1000000))
                            :path spec-path}
                           m)))))
  ([err path]
   (let [{:keys [line explanation]} err]
     (convert-error-locations explanation path line))))

(defn- update-status-location [status path line]
  (if (:err status)
    {:err (convert-error-locations (:err status) path line)}
    status))

(defn- expr-stringifier [v]
  (-> (:langmap v)
      (get (:lang v))
      (get :stringify-expr *stringify-expr*)))

(def lang-spec-sm
  {:any [{:update-fn (fn [v [line]]
                       (-> v
                           (update :line (fnil inc 0))
                           (update-if (and (:generate v)
                                           (not= (:state v) :status))
                                      :generated (fnil #(conj % line) []))))}]
   :init [{:pattern #"[Ll]anguage: +`(.*)`"
           :update-fn (fn [v [_line lang]]
                        (-> v
                            (assoc :lang lang)))}
          {:pattern #".*`(.*)`:"
           :transition :maybe-module
           :update-fn (fn [v [_line module-name]]
                        (assoc v :module-name module-name))}
          {:pattern #"```.*"
           :transition :code
           :update-fn (fn [v _m]
                        (-> v
                            (assoc :code-block-start (:line v))))}]
   :code [{:pattern #"```"
           :transition :maybe-status}
          {:update-fn (fn [v [line]]
                        (-> v
                            (update :current-block (fnil #(conj % line) []))))}]
   :maybe-status
   [{:pattern #"```status"
     :transition :status
     :update-fn (fn [v _m]
                  (let [status (-> (eval-code v)
                                   (update-status-location (:path v)
                                                           (:code-block-start v)))
                        status-text (if (contains? status :err)
                                      (str "ERROR: "
                                           (binding [*stringify-expr* (expr-stringifier v)]
                                             (explanation-to-str (:err status))))
                                      "Success")]
                    (-> v
                        (dissoc :current-block)
                        (assoc :current-status status)
                        (update-if (:generate v)
                                   :generated #(conj % status-text)))))}
    {:transition :init
     :update-fn (fn [v _m]
                  (-> v
                      (dissoc :current-block)
                      (dissoc :code-block-start)))}]
   :status [{:pattern #"[Ss]uccess"
             :transition :post-status
             :update-fn
             (fn [v _m]
               (-> v
                   (update-if (contains? (:current-status v) :err)
                              :errors #(conj % (:err (:current-status v))))
                   (update-if (contains? (:current-status v) :ok)
                              :success (fnil inc 0))))}
            {:pattern #"ERROR: (.*)"
             :transition :post-status
             :update-fn
             (fn [v [_line expected]]
               (let [actual (if (contains? (:current-status v) :err)
                              (binding [*stringify-expr* (expr-stringifier v)]
                                (explanation-to-str (:err (:current-status v))))
                              "")]
                 (-> v
                     (update-if (and (contains? (:current-status v) :err)
                                     (= actual expected))
                                :success (fnil inc 0))
                     (update-if (and (contains? (:current-status v) :err)
                                     (not= actual expected))
                                :errors #(conj % (concat ["The wrong error was reported:"]
                                                         (:err (:current-status v)))))
                     (update-if (contains? (:current-status v) :ok)
                                :errors #(conj % ["The example should have produced an error, but did not"])))))}
            {:update-fn (fn [_v [line]]
                          (throw 
                           (Exception. (str "A status block should contain either 'Success' or 'ERROR: ...', but '"
                                            line
                                            "' was found"))))}]
   :post-status [{:pattern #"```"
                  :transition :init
                  :update-fn
                  (fn [v _m]
                    (-> v
                        (dissoc :code-block-start)
                        (dissoc :current-status)))}
                 {:update-fn (fn [_v [line]]
                               (throw
                                (Exception. (str "A status block should only contain a single line, but '"
                                                 line
                                                 "' was found"))))}]
   :maybe-module [{:pattern #"```.*"
                   :transition :module}
                  {:transition :init
                   :update-fn (fn [v _m]
                                (-> v
                                    (dissoc :module-name)))}]
   :module [{:pattern #"```"
             :transition :init
             :update-fn (fn [v _matches]
                          (-> v
                              (update :modules
                                      (fnil #(assoc % (:module-name v)
                                                    (:module-lines v))
                                            {}))
                              (dissoc :module-name)
                              (dissoc :module-lines)))}
            {:update-fn (fn [v [line]]
                          (-> v
                              (update :module-lines
                                      (fnil #(conj % line) []))))}]})

(defn process-lang-spec [v lines]
  (process-lines lang-spec-sm v lines))