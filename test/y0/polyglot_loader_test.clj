(ns y0.polyglot-loader-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.polyglot-loader :refer :all]
            [y0.status :refer [ok unwrap-status]]))

;; # A Polyglot Module Loader

;; $y_0$ requires a polyglot module system. The reason is that modules of
;; hosted languages are loaded alongside $y_0$ files holding the semantic
;; definition of the language. The hosted language and $y_0$ may require
;; different parsers and possibly different resolution logic (logic for finding
;; files corresponding to a given module on disk), but after parsing, their
;; trees are used together as a single $y_0$ program.

;; One example of this idea is $y_1$, which is defined and being used (in
;; tests) [in the same file](y1.md).

;; This module defines the functions that make up $y_0$'s polyglot module
;; system.

;; ## Module and Language Representation

;; Modules are represented as maps containing the all or part of the following
;; keys:

;; 1. `:lang` (required), containing the language name, as a string.
;; 2. `:name` (required), containing the module name, as a string.
;; 3. `:path`, containing the path to the module file on disk. Filled if
;;    already resolved.
;; 4. `:text`, containig the module's contents, as text. Filled after reading
;;    the file from disk.
;; 5. `:statements`, containing a sequence of parsed statements (parse-tree
;;    fragments).
;; 6. `:deps`, containing a collection of modules (with at least`:lang` and
;;    `:name`), representing the module's dependencies.

;; A language is also represented as a map, with two keys, `:resolve` and
;; `:parse`. Both function take a module and return a [status](status.md) with
;; an updated modules. They add keys to the module they process, as follows:

;; * `:resolve` assumes `:name` and adds `:path`.
;; * `:parse` assumes `:name`, `:path` and `:text` and adds `:statements` and
;;   `:deps`.

;; The collection of all supported languages is contained in a _language map_,
;; which maps a language name (string) to its representation.

;; ### Loading a Single Module

;; The function `load-module` takes a module (as defined above) with partial
;; keys and a language map, and completes it.

;; For the following examples, let us use the following language-map:
(def lang-map {"y1" {:resolve #(ok % assoc :path "some/path")
                     :parse #(-> %
                                 (assoc :statements `[parsing ~(:text %)])
                                 (assoc :deps [{:lang "y1"
                                                :name (str (:name %) 2)}])
                                 ok)}
               "y2" {:resolve #(ok % assoc :path "some/other/path")
                     :parse #(-> %
                                 (assoc :statements [`(something-else ~(:name %))])
                                 (assoc :deps [{:lang "y2"
                                                :name (str (:name %) 3)}])
                                 ok)}})

;; If it contains `:statements`, it does nothing.
(fact
 (load-module {:lang "y1"
               :statements [1 2]} lang-map) => {:ok {:lang "y1"
                                                     :statements [1 2]}})

;; If `:statements` is not present, but `:text` is, `:parse` is called.
(fact
 (load-module {:lang "y1"
               :name "foo"
               :path "path/to/foo.y1"
               :text "text inside foo.y1"} lang-map)
 => {:ok {:lang "y1"
          :name "foo"
          :path "path/to/foo.y1"
          :text "text inside foo.y1"
          :statements `[parsing "text inside foo.y1"]
          :deps [{:lang "y1"
                  :name "foo2"}]}})

;; If `:text` is not present but `:path` is, `slurp` is called to read the
;; text, followed by `:parse` to parse it.
(fact
 (load-module {:lang "y1"
               :name "foo"
               :path "path/to/foo.y1"} lang-map)
 => {:ok {:lang "y1"
          :name "foo"
          :path "path/to/foo.y1"
          :text "text inside foo.y1"
          :statements `[parsing "text inside foo.y1"]
          :deps [{:lang "y1"
                  :name "foo2"}]}}
 (provided
  (slurp "path/to/foo.y1") => "text inside foo.y1"))

;; If `:path` is not provided, but `:name` is, `:resolve` is called to resolve
;; the path.
(fact
 (load-module {:lang "y1"
               :name "foo"} lang-map)
 => {:ok {:lang "y1"
          :name "foo"
          :path "some/path"
          :text "text inside foo.y1"
          :statements `[parsing "text inside foo.y1"]
          :deps [{:lang "y1"
                  :name "foo2"}]}}
 (provided
  (slurp "some/path") => "text inside foo.y1"))

;; ## Module Store

;; A module ID is a string of the form `land:name`. The function `module-id`
;; takes a module (map) and returns an ID (string).
(fact
 (module-id {:lang "foo" :name "bar"}) => "foo:bar")

;; A module-store is a map, mapping module IDs to the respective completed
;; modules. `load-with-deps` takes a single (partial) module and a language-map
;; and returns a module-store containing it along with all its dependencies.

;; To demonstrate it, we start by building a dependency map of modules.
(def my-deps
  {"m1" []
   "m2" ["m1"]
   "m3" ["m1"]
   "m4" ["m2"]
   "m5" ["m1"]
   "m6" ["m3" "m2"]
   "m7" ["m1"]
   "m8" ["m4"]
   "m9" ["m3"]
   "m10" ["m5" "m2"]
   "m11" ["m1"]
   "m12" ["m6" "m4"]})

;; Yes, it was a bit nurdy/lazy of me, but instead of using real names I just
;; named my modules after natural numbers from 1 to 12 and gave each number as
;; dependencies the modules with the numbers of the divisors of that number,
;; omitting devisors of those devisors. For example, for `m12` I wrote `m6` and
;; `m4`, but not `m3`, because it is already a divisor (dependency) of `m6`.

;; OK, so after this completely unnecessary detour in middle-school math, we
;; continue to defining our language-map. We will only have one language here,
;; `y2`. The resolver will be trivial, matching module `foo` with path
;; `/foo.y2`. The parser will provide fixed `:statements`, but will provide
;; dependencies from `my-deps`.
(def lang-map2
  {"y2" {:resolve (fn [{:keys [name] :as m}]
                    (ok m assoc :path (str "/" name ".y2")))
         :parse (fn [{:keys [name text] :as m}]
                  (ok (-> m
                          (assoc :statements [name text])
                          (assoc :deps (for [dep (my-deps name)]
                                         {:lang "y2"
                                          :name dep})))))}})

;; Now we have what we need to call `load-with-deps`.
(fact
 (def my-mstore (unwrap-status (load-with-deps {:lang "y2" :name "m12"} lang-map2)))
 => #'my-mstore
 (provided
  (slurp "/m12.y2") => "text in m12"
  (slurp "/m6.y2") => "text in m6"
  (slurp "/m4.y2") => "text in m4"
  (slurp "/m3.y2") => "text in m3"
  (slurp "/m2.y2") => "text in m2"
  (slurp "/m1.y2") => "text in m1")
 my-mstore => {"y2:m12" {:lang "y2"
                         :name "m12"
                         :path "/m12.y2"
                         :text "text in m12"
                         :statements ["m12" "text in m12"]
                         :deps [{:lang "y2" :name "m6"}
                                {:lang "y2" :name "m4"}]}
               "y2:m6" {:lang "y2"
                        :name "m6"
                        :path "/m6.y2"
                        :text "text in m6"
                        :statements ["m6" "text in m6"]
                        :deps [{:lang "y2" :name "m3"}
                               {:lang "y2" :name "m2"}]}
               "y2:m4" {:lang "y2"
                        :name "m4"
                        :path "/m4.y2"
                        :text "text in m4"
                        :statements ["m4" "text in m4"]
                        :deps [{:lang "y2" :name "m2"}]}
               "y2:m3" {:lang "y2"
                        :name "m3"
                        :path "/m3.y2"
                        :text "text in m3"
                        :statements ["m3" "text in m3"]
                        :deps [{:lang "y2" :name "m1"}]}
               "y2:m2" {:lang "y2"
                        :name "m2"
                        :path "/m2.y2"
                        :text "text in m2"
                        :statements ["m2" "text in m2"]
                        :deps [{:lang "y2" :name "m1"}]}
               "y2:m1" {:lang "y2"
                        :name "m1"
                        :path "/m1.y2"
                        :text "text in m1"
                        :statements ["m1" "text in m1"]
                        :deps []}})

;; ### Topological Sorting of a Module Store

;; Given a module-store, we wish to find an order in which we could evaluate
;; modules, so that all dependencies of a given module are evaluated before it.

;; This requires us to topologically-sort a graph, in which the nodes are
;; modules and the edges are dependencies.

;; A topological sort of a graph starts with source nodes. These are nodes that
;; do not have any edge go into them. In our case, these are the modules that
;; have no dependencies.

;; `mstore-sources` takes a module-store and returns a set of source module
;; IDs.
(fact
 (mstore-sources my-mstore) => #{"y2:m1"})

;; Next, the algorithm needs to walk from these sources to all the nodes
;; reachable from them through forward edges, listing all the nodes it
;; encounters in-order. Unfortunately, `:deps` provides _backward_ edges. To
;; fix this, `mstore-refs` generates an inverse index of `:deps`, creating a
;; map from module-IDs to the IDs of modules that directly depend on them.
(fact
 (mstore-refs my-mstore) => {"y2:m1" #{"y2:m2" "y2:m3"}
                             "y2:m2" #{"y2:m4" "y2:m6"}
                             "y2:m3" #{"y2:m6"}
                             "y2:m4" #{"y2:m12"}
                             "y2:m6" #{"y2:m12"}})

;; `mstore-toposort` does the actual sorting. It takes a module-store and
;; returns a sequence of module-IDs, given in some topological order.
(fact
 (mstore-toposort my-mstore) => ["y2:m1" "y2:m3" "y2:m2" "y2:m6" "y2:m4" "y2:m12"])

;; Please note that the order above is only one possible sort (`m2` can come
;; before `m3` and `m4` can come before `m6`), but Clojure has stable
;; representations for maps and sets, making it possible to write a test with
;; one particular result. Please note that this particular result is
;; topologically sorted. `m1` comes first, `m12` comes last; `m3` and `m2`
;; precede `m6`, etc. 