(ns y0.modules-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.modules :refer :all]
            [clojure.java.io :as io]))

;; ## Module Names and Paths

;; Similar to Python, Java and Clojure, $y_0$ modules are given names that correspond to their paths in the file system,
;; to allow the module system to find them within the file-system.
;; Like Python and Java, $y_0$ has a `Y0_PATH`, an ordered set of path prefixes in which the module system should look
;; for modules.
;;
;; `module-paths` takes a dot-separated module name and a sequence of base paths (the `Y0_PATH`) and returns a sequence
;; of `java.io.File` objects representing the different candidate paths for this module.
(fact
 (module-paths "foo.bar.baz" ["/one/path" "/two/path"]) => [(io/file "/one/path" "foo" "bar" "baz.y0")
                                                            (io/file "/two/path" "foo" "bar" "baz.y0")])

;; The function `read-module` (not shown here) uses `module-paths` to determine the path candidates for the module,
;; and reads (as string) the first one that exists.

;; ## Loading Modules

;; The function `load-single-module` takes a module name and the `y0-path` as a list of paths,
;; and returns a pair consisting of a list of statements read from the module (with namespaces translated)
;; and a list of module names to be further loaded.
;; To retrieve the module file's contents it calls `read-module`.
(fact
 (load-single-module "foo.bar" ["/some/path"]) => ['[(foo.bar/a baz.quux/x)] ["baz.quux"]]
 (provided
  (read-module "foo.bar" ["/some/path"]) => ["(ns foo.bar
                                               (:require [baz.quux :as baz :refer [x y z]]))
                                              (a x)" "bar.y0"]))

;; The `y0` module is a special one in that it does not contain a set of statements but rather the semantics of the language itself.
;; As such, it defines the `<-` symbol, which is implicitly associated with the `y0` namespace in the `refer-map`.
;; Additional symbols in the `y0` namespace: `...` and `test`.
(fact
 (load-single-module "foo.bar" ["/some/path"]) => ['[(y0.core/<- (foo.bar/a :x)
                                                              (foo.bar/b :x))
                                                     (y0.core/assert)
                                                     (y0.core/all [] foo.bar/foo)] []]
 (provided
  (read-module "foo.bar" ["/some/path"]) => ["(ns foo.bar)
                                              (<- (a :x) (b :x))
                                              (assert)
                                              (all [] foo)" "bar.y0"]))

;; When reading the module, the source location of expressions is recorded as metadata.
;; The file path is recorded as `:path` and the beginning and end locations within the
;; file are recorded as `:start` and `:end`, respectively, representing the row and
;; column location as a single integrer using the formula: `row * 1000000 + col`. This
;; guarantees that (1) the values of the row and column can be extracted unambiguously
;; from the values and (2) these values can be compared using normal integer comparison
;; operators (e.g., `>` and `<=`).
(fact
 (let [[module _deps] (load-single-module "foo.bar" ["/some/path"])]
   (def foobar-module module)) => #'y0.modules-test/foobar-module
 (provided
  (read-module "foo.bar" ["/some/path"]) => ["(ns foo.bar)
                                                  (a b)
                                                  (charlie)" "foo.y0"])
 ;; Location of `(a b)`
 (-> foobar-module first meta)  => {:path "foo.y0" :start 2000051 :end 2000056}
 ;; Location of `a`
 (-> foobar-module first first meta)  => {:path "foo.y0" :start 2000052 :end 2000053}
 ;; Location of `b`
 (-> foobar-module first second meta)  => {:path "foo.y0" :start 2000054 :end 2000055}
 ;; Location of `charlie`
 (-> foobar-module second first meta)  => {:path "foo.y0" :start 3000052 :end 3000059})



;; ## Loading a Complete Program

;; Loading a program is performed in two stages. First the function `load-all-modules` loads all
;; the requested modules and their recursive dependencies, to populate a map, mapping module names
;; to `[statements deps]` pairs.
(fact
 (def loaded-modules (load-all-modules ["a" "b"] ["/some/path"])) => var?
 (provided
  (read-module "a" ["/some/path"]) => ["(ns a (:require [b :refer [B]] [c :refer [C]]))
                                         A* B C" "a.y0"]
  (read-module "b" ["/some/path"]) => ["(ns b (:require [c :refer [C]] [d :refer [D]]))
                                         B* C D" "b.y0"]
  (read-module "c" ["/some/path"]) => ["(ns c (:require [d :refer [D]]))
                                         C* D" "c.y0"]
  (read-module "d" ["/some/path"]) => ["(ns d)
                                         D*" "d.y0"])
 loaded-modules => {"a" ['[a/A* b/B c/C] ["b" "c"]]
                    "b" ['[b/B* c/C d/D] ["c" "d"]]
                    "c" ['[c/C* d/D] ["d"]]
                    "d" ['[d/D*] []]})

;; The second step involves topologically-sorting the statements loaded from the modules to a single
;; stream of statements that respects dependencies such as the statements from dependencies of a given
;; module will appear before the module's statements.

;; The function `sort-statements-by-deps` takes the map returned by load-all-modules and returns a
;; combined list of topologically-sotred statements.
(fact
 (sort-statements-by-deps loaded-modules) => '[d/D*
                                               c/C* d/D
                                               b/B* c/C d/D
                                               a/A* b/B c/C])

;; `load-with-dependencies` combines the two operations to read all requested modules along with
;; their dependencies.

(fact
 (load-with-dependencies ["test.a"] ["/some/path"]) =>
 '[[(test.c/baz 42)
    (test.b/bar test.c/baz)
    (test.a/foo test.b/bar test.c/baz)]
   #{"test.a" "test.b" "test.c"}]
 (provided
  (read-module "test.a" ["/some/path"]) => ["(ns test.a
                                              (:require [test.b :as b]
                                                        [test.c :as c]))
                                             (foo b/bar c/baz)" "a.y0"]
  (read-module "test.b" ["/some/path"]) => ["(ns test.b
                                              (:require [test.c :as c]))
                                             (bar c/baz)" "b.y0"]
  (read-module "test.c" ["/some/path"]) => ["(ns test.c)
                                             (baz 42)" "c.y0"]))

