(ns y0.edn-parser-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.edn-parser :refer :all]
            [y0.status :refer [let-s ok]]
            [clojure.string :as str]))

;; # Parsing EDN-Based Languages

;; The [Extensible Data Notation](https://github.com/edn-format/edn) is a
;; simplified version of Clojure's s-expression syntax, intended as a data
;; format, think JSON, just one that is more suitable for processing by
;; declarative languages (think Clojure), rather than object-oriented languages
;; (think Javascript).

;; $y_0$'s syntax is EDN-based. This module defines a parser for parsing
;; EDN-based languages, including $y_0$ itself. It is pluggable to $y_0$'s
;; [module system](polyglot_loader.md).

;; ## Namespace Conversions

;; Namespace conversion is the process of converting the symbols used in a $y_0$
;; module from their _local_ form, i.e., relative to the definitions of the
;; module, to their _global_ form, i.e., using absolute namespace names.
;;
;; The function `convert-ns` takes an s-expression and two maps. It
;; [walks](term_utils.md#meta-preserving-postwalk) through the expression,
;; converting symbols based on them.
;;
;; The first map is the `ns-map`, converting local namespace aliases into global
;; namespace IDs. A `nil` key represents the default namespace.
(fact
 (convert-ns 3 {nil "/bar/baz"} {}) => 3
 (convert-ns 'foo {nil "/bar/baz"} {}) => (symbol "/bar/baz" "foo")
 (convert-ns '(foo x/quux 3) {nil "/bar/baz"
                              "x" "/path/to/xeon"} {}) =>
 (list (symbol "/bar/baz" "foo") (symbol "/path/to/xeon" "quux") 3))

;; If a namespace is not found in the `ns-map`, an exception is thrown.
(fact
 (convert-ns 'unknown/foo {nil "/bar/baz"} {}) =>
 (throws "Undefined namespace: unknown"))

;; The second map is the `refer-map`, similar to the `:refer` operator in Clojure `:require` expressions.
;; It maps the names of namspace-less symbols into namespaces they are provided with, as override over
;; the `ns-map`.
(fact
 (convert-ns 'foo {nil "/bar/baz"} {"foo" "/path/to/xeon"}) =>
 (symbol "/path/to/xeon" "foo")
 (convert-ns '(foo bar) {nil "default"} {"foo" "/path/to/xeon"}) =>
 (list (symbol "/path/to/xeon" "foo") (symbol "default" "bar"))
 (convert-ns 'x/foo {nil "default" "x" "/path/to/xeon"} {"foo" "/path/to/xeon"}) =>
 (symbol "/path/to/xeon" "foo"))

;; `convert-ns` preserves the metadata of the symbols it converts.
(fact
 (-> 'foo
     (with-meta {:x 1 :y 2})
     (convert-ns {nil ".bar/baz"
                  "x" "/path/to/xeon"} {})
     meta) => {:x 1 :y 2})

;; ## Parsing `ns` Forms

;; Inspired by Clojure, every EDN-based module begins with a `ns` declaration.
;; This declaration is translated into the following pieces of information:
;; * A list of module names to be loaded (dependencies).
;; * A `ns-map`, translating local namespaces into global ones, and
;; * A `refer-map`, providing namespaces to specific namespace-less symbols.
;;
;; `parse-ns-decl` takes a `ns` declaration and a `resolve` function as
;; parameters and returns a tuple of the above outputs.

;; For the examples below, we consider a `dummy-resolve`, which replaces dots
;; with slashes in the name to form a path (and prepends a slash and appends an
;; extension). In actuality, this has to be an actual resolution function which
;; resolves a name of a module into an absolute path.
(defn- dummy-resolve [name]
  (str "/" (str/replace name #"\." "/") ".y0"))

(fact
 (parse-ns-decl '(ns foo.bar) dummy-resolve) => [[] {nil "/foo/bar.y0"} {}])

;; The module name is followed by a sequence of directives. The `require` directive instructs the module system
;; to load another module and assigns an alias to it.
;; Optionally, a vector of "refer" symbols is also provided.
;; These symbols will be implicitly associated with that namespace when written without a namespace prefix.
(fact
 (parse-ns-decl '(ns foo.bar
                   (:require [baz.quux :as quux]
                             [baz.puux :refer [x]]
                             [baz.y0ux :as muux :refer [a b c]]))
                dummy-resolve) =>
 [["/baz/quux.y0" "/baz/puux.y0" "/baz/y0ux.y0"]
  {nil "/foo/bar.y0"
   "quux" "/baz/quux.y0"
   "muux" "/baz/y0ux.y0"}
  {"a" "/baz/y0ux.y0"
   "b" "/baz/y0ux.y0"
   "c" "/baz/y0ux.y0"
   "x" "/baz/puux.y0"}])

;; ## Converting Code Locations

;; [Edamame](https://github.com/borkdude/edamame), the EDN parser we are using,
;; stores the code location of every part of the s-expression that it reads as
;; meta, using the keys `:row`, `:col`, `:end-row` and `:end-col`.

;; A canonical input to $y_0$ should be annotated with meta, containing the
;; properties `:start`, `:end` and `:path`, where the former two are integers
;; encompasing the row and column using the formula `row*10^6 + col`.

;; The function `convert-location` takes an Edamame-style location and converts
;; it to a $y_0$ canonical form.
(fact
 (convert-location {:row 1 :col 2 :end-row 3 :end-col 4}) =>
 {:start 1000002 :end 3000004})

;; ## The EDN Parser

;; `edn-parser` generates a parser for EDN-based languages. It takes the
;; following paramters:
;; 1. An initial `refer-map` map, which maps symbols (as strings) to namespaces.
;;    This should be used to map the language's root namespace's symbols to the
;;    root namespace, similar to `clojure.core` in Clojure.
;; 2. A list of modules to be added to the dependency list for every module in
;;    the language. This is intended for injecting the language's semantic
;;    definition as a dependency of each module in the language.

;; It returns a _parser_, i.e., a function that takes a module name, a module
;; path and the textual contents of a module and returns a status containing a
;;pair: the canonical parse of the module (as a list of statements) and a list
;; of dependencies, modules required by this one.
(fact
 (let [root-symbols '[foo bar baz]
       root-refer-map (into {} (for [sym root-symbols]
                                 [(name sym) "mylang.core"]))
       parse (edn-parser root-refer-map "y7" ["/path/to/y7.y0"])
       status (parse "boo" "/boo.y0"
                     "(ns boo (:require [some.module]))\na foo goes into a bar"
                     dummy-resolve)
       {:keys [ok]} status
       [statements deps] ok]
   statements => [(symbol "/boo.y0" "a")
                  (symbol "mylang.core" "foo")
                  (symbol "/boo.y0" "goes")
                  (symbol "/boo.y0" "into")
                  (symbol "/boo.y0" "a")
                  (symbol "mylang.core" "bar")]
   (map meta statements) => [{:start 2000001 :end 2000002 :path "/boo.y0"}
                             {:start 2000003 :end 2000006 :path "/boo.y0"}
                             {:start 2000007 :end 2000011 :path "/boo.y0"}
                             {:start 2000012 :end 2000016 :path "/boo.y0"}
                             {:start 2000017 :end 2000018 :path "/boo.y0"}
                             {:start 2000019 :end 2000022 :path "/boo.y0"}]
   deps => ["/some/module.y0"
            "/path/to/y7.y0"]))

;; In case of a parsing error, an `:err` status is returned.
(fact
 (let [root-symbols '[foo bar baz]
       root-refer-map (into {} (for [sym root-symbols]
                                 [(name sym) "mylang.core"]))
       parse (edn-parser root-refer-map "y7" [{:lang "y0" :name "y7.semantics"}])
       status (parse "boo" "/path/to/boo"
                     "(ns boo (:require [some.module]))\na foo goes into a bar (")
       {:keys [ok err]} status
       {:keys [error]} err]
   ok => nil?
   error => "EOF while reading, expected ) to match ( at [2,23]"))

;; ## Root Module Symbols

;; Languages often have their collection of symbols that are imported by default
;; from some root module. To accommodate this in EDN-based languages, the
;; function `root-module-symbols` generates a `refer-map` for `edn-parser`,
;; given a list of root-module symbols and the name of the root module.
(fact
 (root-module-symbols '[foo bar baz] "my.root") => {"foo" "my.root"
                                                    "bar" "my.root"
                                                    "baz" "my.root"})

;; The symbols can also be given as strings.
(fact
 (root-module-symbols '["foo" "bar" "baz"] "my.root") => {"foo" "my.root"
                                                          "bar" "my.root"
                                                          "baz" "my.root"})
