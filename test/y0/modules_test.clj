(ns y0.modules-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.modules :refer :all]
            [clojure.java.io :as io]))

;; ## Namespace Conversions

;; Namespace conversion is the process of converting the symbols used in a y0 module from their
;; _local_ form, i.e., relative to the definitions of the module, to their _global_ form,
;; i.e., using absolute namespace names.
;;
;; The function `convert-ns` takes an s-expression and two maps.
;; It [walks](https://clojuredocs.org/clojure.walk) through the expression, converting symbols based on them.
;;
;; The first map is the `ns-map`, converting local namespace aliases into global namespace IDs.
;; A `nil` key represents the default namespace.
(fact
 (convert-ns 3 {nil "bar.baz"} {}) => 3
 (convert-ns 'foo {nil "bar.baz"} {}) => 'bar.baz/foo
 (convert-ns '(foo x/quux 3) {nil "bar.baz"
                              "x" "xeon"} {}) => '(bar.baz/foo xeon/quux 3))

;; If a namespace is not found in the `ns-map`, an exception is thrown.
(fact
 (convert-ns 'unknown/foo {nil "bar.baz"} {}) => (throws "Undefined namespace: unknown"))

;; The second map is the `refer-map`, similar to the `:refer` operator in Clojure `:require` expressions.
;; It maps the names of namspace-less symbols into namespaces they are provided with, as override over
;; the `ns-map`.
(fact
 (convert-ns 'foo {nil "bar.baz"} {"foo" "quux"}) => 'quux/foo
 (convert-ns '(foo bar) {nil "default"} {"foo" "quux"}) => '(quux/foo default/bar)
 (convert-ns 'x/foo {nil "default" "x" "xeon"} {"foo" "quux"}) => 'xeon/foo)

;; `convert-ns` preserves the metadata of the symbols it converts.
(fact
 (-> 'foo
     (with-meta {:x 1 :y 2})
     (convert-ns {nil "bar.baz"
                  "x" "xeon"} {})
     meta) => {:x 1 :y 2})

;; ## Module Names and Paths

;; Similar to Python, Java and Clojure, y0 modules are given names that correspond to their paths in the file system,
;; to allow the module system to find them within the file-system.
;; Like Python and Java, y0 has a `Y0_PATH`, an ordered set of path prefixes in which the module system should look
;; for modules.
;;
;; `module-paths` takes a dot-separated module name and a sequence of base paths (the `Y0_PATH`) and returns a sequence
;; of `java.io.File` objects representing the different candidate paths for this module.
(fact
 (module-paths "foo.bar.baz" ["/one/path" "/two/path"]) => [(io/file "/one/path" "foo" "bar" "baz.mu")
                                                            (io/file "/two/path" "foo" "bar" "baz.mu")])

;; The function `read-module` (not shown here) uses `module-paths` to determine the path candidates for the module,
;; and reads (as string) the first one that exists.

;; ## Parsing a Module

;; Every module begins with a `ns` declaration, inspired by Clojure.
;; This declaration is translated into the following pieces of information:
;; * A list of module names to be loaded (dependencies).
;; * A `ns-map`, translating local namespaces into global ones, and
;; * A `refer-map`, providing namespaces to specific namespace-less symbols.
;;
;; `parse-ns-decl` takes a `ns` declaration as parameter and returns a tuple of the above outputs.
(fact
 (parse-ns-decl '(ns foo.bar)) => [[] {nil "foo.bar"} {}])

;; The module name is followed by a sequence of directives. The `require` directive instructs the module system
;; to load another module and assigns an alias to it.
;; Optionally, a vector of "refer" symbols is also provided.
;; These symbols will be implicitly associated with that namespace when written without a namespace prefix.
(fact
 (parse-ns-decl '(ns foo.bar
                    (:require [baz.quux :as quux]
                              [baz.puux :refer [x]]
                              [baz.muux :as muux :refer [a b c]]))) =>
 [["baz.quux" "baz.puux" "baz.muux"]
  {nil "foo.bar"
   "quux" "baz.quux"
   "muux" "baz.muux"}
  {"a" "baz.muux"
   "b" "baz.muux"
   "c" "baz.muux"
   "x" "baz.puux"}])

;; The function `load-single-module` takes a module name and the `y0-path` as a list of paths,
;; and returns a pair consisting of a list of statements read from the module (with namespaces translated)
;; and a list of module names to be further loaded.
;; To retrieve the module file's contents it calls `read-module`.
(fact
 (load-single-module "foo.bar" ["/some/path"]) => ['[(foo.bar/a baz.quux/x)] ["baz.quux"]]
 (provided
  (read-module "foo.bar" ["/some/path"]) => "(ns foo.bar
                                              (:require [baz.quux :as baz :refer [x y z]]))
                                              (a x)"))

;; The `y0` module is a special one in that it does not contain a set of statements but rather the semantics of the language itself.
;; As such, it defines the `<-` symbol, which is implicitly associated with the `y0` namespace in the `refer-map`.
;; Additional symbols in the `y0` namespace: `...` and `test`.
(fact
 (load-single-module "foo.bar" ["/some/path"]) => ['[(y0/<- (foo.bar/a :x)
                                                              (foo.bar/b :x))
                                                     (y0/test y0/...)
                                                     (y0/clj-step y0/return y0/continue)] []]
 (provided
  (read-module "foo.bar" ["/some/path"]) => "(ns foo.bar)
                                             (<- (a :x) (b :x))
                                             (test ...)
                                             (clj-step return continue)"))

;; ## Loading a Complete Program

;; The loading of a complete program is done module-by-module.
;; The state of the loading consists of the following:
;; * A list of the modules waiting to be loaded.
;; * A list of the statements loaded so far.
;; * A set of the modules loaded so far.
;;
;; A loading step takes the first pending module and loads it.
;; It prepends its statements to the statement list and prepends the modules it requires to the pending module list.
;; It also adds the loaded module to the loaded modules set to avoid loading this module again.
;;
;; `load-with-dependencies` takes the name of a "main" module and a list of `y0-path` and
;; returns a pair (`statements`, `modules`) where `statements` is an aggregated list of statements that were loaded and
;; `modules` is a set of modules that were loaded.
(fact
 (load-with-dependencies "test.a" ["/some/path"]) => '[[(test.c/baz 42)
                                                        (test.b/bar test.c/baz)
                                                        (test.a/foo test.b/bar test.c/baz)]
                                                       #{"test.a" "test.b" "test.c"}]
 (provided
  (read-module "test.a" ["/some/path"]) => "(ns test.a
                                             (:require [test.b :as b]
                                                       [test.c :as c]))
                                            (foo b/bar c/baz)"
  (read-module "test.b" ["/some/path"]) => "(ns test.b
                                             (:require [test.c :as c]))
                                            (bar c/baz)"
  (read-module "test.c" ["/some/path"]) => "(ns test.c)
                                            (baz 42)"))