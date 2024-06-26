  * [Namespace Conversions](#namespace-conversions)
  * [Module Names and Paths](#module-names-and-paths)
  * [Parsing a Module](#parsing-a-module)
  * [Loading a Complete Program](#loading-a-complete-program)
```clojure
(ns y0.modules-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.modules :refer :all]
            [clojure.java.io :as io]))

```
## Namespace Conversions

Namespace conversion is the process of converting the symbols used in a $y_0$ module from their
_local_ form, i.e., relative to the definitions of the module, to their _global_ form,
i.e., using absolute namespace names.
The function `convert-ns` takes an s-expression and two maps.
It [walks](https://clojuredocs.org/clojure.walk) through the expression, converting symbols based on them.
The first map is the `ns-map`, converting local namespace aliases into global namespace IDs.
A `nil` key represents the default namespace.
```clojure
(fact
 (convert-ns 3 {nil "bar.baz"} {}) => 3
 (convert-ns 'foo {nil "bar.baz"} {}) => 'bar.baz/foo
 (convert-ns '(foo x/quux 3) {nil "bar.baz"
                              "x" "xeon"} {}) => '(bar.baz/foo xeon/quux 3))

```
If a namespace is not found in the `ns-map`, an exception is thrown.
```clojure
(fact
 (convert-ns 'unknown/foo {nil "bar.baz"} {}) => (throws "Undefined namespace: unknown"))

```
The second map is the `refer-map`, similar to the `:refer` operator in Clojure `:require` expressions.
It maps the names of namspace-less symbols into namespaces they are provided with, as override over
the `ns-map`.
```clojure
(fact
 (convert-ns 'foo {nil "bar.baz"} {"foo" "quux"}) => 'quux/foo
 (convert-ns '(foo bar) {nil "default"} {"foo" "quux"}) => '(quux/foo default/bar)
 (convert-ns 'x/foo {nil "default" "x" "xeon"} {"foo" "quux"}) => 'xeon/foo)

```
`convert-ns` preserves the metadata of the symbols it converts.
```clojure
(fact
 (-> 'foo
     (with-meta {:x 1 :y 2})
     (convert-ns {nil "bar.baz"
                  "x" "xeon"} {})
     meta) => {:x 1 :y 2})

```
## Module Names and Paths

Similar to Python, Java and Clojure, $y_0$ modules are given names that correspond to their paths in the file system,
to allow the module system to find them within the file-system.
Like Python and Java, $y_0$ has a `Y0_PATH`, an ordered set of path prefixes in which the module system should look
for modules.
`module-paths` takes a dot-separated module name and a sequence of base paths (the `Y0_PATH`) and returns a sequence
of `java.io.File` objects representing the different candidate paths for this module.
```clojure
(fact
 (module-paths "foo.bar.baz" ["/one/path" "/two/path"]) => [(io/file "/one/path" "foo" "bar" "baz.y0")
                                                            (io/file "/two/path" "foo" "bar" "baz.y0")])

```
The function `read-module` (not shown here) uses `module-paths` to determine the path candidates for the module,
and reads (as string) the first one that exists.

## Parsing a Module

Every module begins with a `ns` declaration, inspired by Clojure.
This declaration is translated into the following pieces of information:
* A list of module names to be loaded (dependencies).
* A `ns-map`, translating local namespaces into global ones, and
* A `refer-map`, providing namespaces to specific namespace-less symbols.
`parse-ns-decl` takes a `ns` declaration as parameter and returns a tuple of the above outputs.
```clojure
(fact
 (parse-ns-decl '(ns foo.bar)) => [[] {nil "foo.bar"} {}])

```
The module name is followed by a sequence of directives. The `require` directive instructs the module system
to load another module and assigns an alias to it.
Optionally, a vector of "refer" symbols is also provided.
These symbols will be implicitly associated with that namespace when written without a namespace prefix.
```clojure
(fact
 (parse-ns-decl '(ns foo.bar
                    (:require [baz.quux :as quux]
                              [baz.puux :refer [x]]
                              [baz.y0ux :as muux :refer [a b c]]))) =>
 [["baz.quux" "baz.puux" "baz.y0ux"]
  {nil "foo.bar"
   "quux" "baz.quux"
   "muux" "baz.y0ux"}
  {"a" "baz.y0ux"
   "b" "baz.y0ux"
   "c" "baz.y0ux"
   "x" "baz.puux"}])

```
The function `load-single-module` takes a module name and the `y0-path` as a list of paths,
and returns a pair consisting of a list of statements read from the module (with namespaces translated)
and a list of module names to be further loaded.
To retrieve the module file's contents it calls `read-module`.
```clojure
(fact
 (load-single-module "foo.bar" ["/some/path"]) => ['[(foo.bar/a baz.quux/x)] ["baz.quux"]]
 (provided
  (read-module "foo.bar" ["/some/path"]) => ["(ns foo.bar
                                               (:require [baz.quux :as baz :refer [x y z]]))
                                              (a x)" "bar.y0"]))

```
The `y0` module is a special one in that it does not contain a set of statements but rather the semantics of the language itself.
As such, it defines the `<-` symbol, which is implicitly associated with the `y0` namespace in the `refer-map`.
Additional symbols in the `y0` namespace: `...` and `test`.
```clojure
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

```
When reading the module, the source location of expressions is recorded as metadata.
```clojure
(fact
 (let [[module _deps] (load-single-module "foo.bar" ["/some/path"])]
   (def foobar-module module)) => #'y0.modules-test/foobar-module
 (provided
  (read-module "foo.bar" ["/some/path"]) => ["(ns foo.bar)
                                                  (a b)
                                                  (charlie)" "foo.y0"])
 ;; Location of `(a b)`
 (-> foobar-module first meta)  => {:path "foo.y0" :row 2 :col 51 :end-row 2 :end-col 56}
 ;; Location of `a`
 (-> foobar-module first first meta)  => {:path "foo.y0" :row 2 :col 52 :end-row 2 :end-col 53}
 ;; Location of `b`
 (-> foobar-module first second meta)  => {:path "foo.y0" :row 2 :col 54 :end-row 2 :end-col 55}
 ;; Location of `charlie`
 (-> foobar-module second first meta)  => {:path "foo.y0" :row 3 :col 52 :end-row 3 :end-col 59})



```
## Loading a Complete Program

Loading a program is performed in two stages. First the function `load-all-modules` loads all
the requested modules and their recursive dependencies, to populate a map, mapping module names
to `[statements deps]` pairs.
```clojure
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

```
The second step involves topologically-sorting the statements loaded from the modules to a single
stream of statements that respects dependencies such as the statements from dependencies of a given
module will appear before the module's statements.

The function `sort-statements-by-deps` takes the map returned by load-all-modules and returns a
combined list of topologically-sotred statements.
```clojure
(fact
 (sort-statements-by-deps loaded-modules) => '[d/D*
                                               c/C* d/D
                                               b/B* c/C d/D
                                               a/A* b/B c/C])

```
`load-with-dependencies` combines the two operations to read all requested modules along with
their dependencies.

```clojure
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

```

