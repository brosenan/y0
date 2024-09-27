(ns y0.spec-analyzer
  (:require [y0.polyglot-loader :refer [load-with-deps eval-mstore]]
            [y0.status :refer [ok let-s ->s]]
            [y0.rules :refer [apply-statements]]
            [y0.builtins :refer [add-builtins]]
            [clojure.string :refer [join]]))

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

(defn- update-if [m pred key f]
  (if (pred m)
    (update m key f)
    m))

(def lang-spec-sm
  {:any [{:update-fn (fn [v [line]]
                       (-> v
                           (update :line (fnil inc 0))
                           (update-if :generate
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
     :update-fn
     (fn [v _m]
       (let [status (let-s [mstore (load-with-deps [{:lang (:lang v)
                                                     :name "example"
                                                     :path "example"
                                                     :text (join "\n" (:current-block v))}]
                                                   (:langmap v))
                            ps (ok (add-builtins {}))]
                           (eval-mstore mstore #(apply-statements %2 %1 {}) ps))]
         (-> v
             (dissoc :current-block)
             (assoc :current-status status))))}
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
                   (dissoc :code-block-start)
                   (dissoc :current-status)
                   (update-if (constantly (contains? (:current-status v) :err))
                              :errors #(conj % {:line (:code-block-start v)
                                                :explanation (:err (:current-status v))}))))}]
   :post-status [{:pattern #"```"
                  :transition :init
                  :update-fn (fn [v _m]
                               (-> v
                                   (update :code-examples (fnil inc 0))))}]
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