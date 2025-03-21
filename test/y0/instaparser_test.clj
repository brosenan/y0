(ns y0.instaparser-test
  (:require [midje.sweet :refer [fact => throws]]
            [y0.instaparser :refer :all]
            [y0.status :refer [ok]]
            [clojure.string :as str]))

;; # Instaparse Parser

;; [Instaparse](https://github.com/Engelberg/instaparse) is a Clojure parser
;; generator library. It offers a rich language for defining context-free
;; grammars focusing on accepting a wide variety of syntaxes (e.g., dealing with
;; ambiguity) as well as performance.

;; While this is not a part of the $y_0$ language, we choose to offer this
;; link to Instaparse as part of the library, in order to allow codeless
;; definitions of languages that can be defined based on this feature.

;; ## Instaparse Grammars

;; The function `instaparse-grammar` takes a string and returns an Instaparse
;; parser.
(fact
 (let [parser (instaparse-grammar "S = AB;
                                   AB = A B
                                   A = 'a'+
                                   B = 'b'+")]
   (parser "aabb") => [:S [:AB [:A "a" "a"] [:B "b" "b"]]]))
   
;; By default, Instaparse is a
;; [scannerless](https://en.wikipedia.org/wiki/Scannerless_parsing) parser
;; generator. This means that the generated parser works its way directly from
;; the level of characters in the input into a parse-tree, skipping the
;; tokenization phase, which exists in more traditional stacks (such as
;; Lex+Yacc).

;; Scannerless parsing is more powerful than scanner-based parsing, but it does
;; raise the need for accounting for whitespace and comments (which we will
;; commonly refer to as _layout_) practically everywhere in the grammar.

;; Instaparse supports this through their
;; [Auto Whitespace](https://github.com/Engelberg/instaparse/blob/master/docs/ExperimentalFeatures.md#auto-whitespace)
;; experimental feature. Because we believe this feature is useful for most
;; grammar-based programming languages, we add built-in support for it.

;; To support this, a grammar definition may consist of two parts. The first is
;; the main grammar. Then, if the separator `--layout--` is present, what
;; follows is interpreted as the grammar for the layout, which must start with
;; the symbol `layout`.
(fact
 (let [parser (instaparse-grammar "S = AB;
                                   AB = A B
                                   A = 'a'+
                                   B = 'b'+
                                   --layout--
                                   layout = #'\\s'+")]
   (parser "a a bb") => [:S [:AB [:A "a" "a"] [:B "b" "b"]]]))

;; ## Translating Code Locations

;; In order for $y_0$ to properly provide point out errors in parse trees
;; generated by Instaparse, the code locations provided by instaparse need to be
;; translated to the convention used by $y_0$.

;; The function `add-locations` takes a parse-tree produced by an Instaparse
;; parser and adds $y_0$ location information to the nodes.
(fact
 (let [parser (instaparse-grammar "S = AB;
                                    AB = A B
                                    A = 'a'+
                                    B = 'b'+
                                    --layout--
                                    layout = #'\\s'+")
       tree (parser "a a bb")
       tree-with-locs (add-locations tree "foo.txt")]
   tree-with-locs => [:S [:AB [:A "a" "a"] [:B "b" "b"]]]
   (-> tree-with-locs meta) => {:path "foo.txt" :start 1000001 :end 1000007}
   ;; Location of "a a"
   (-> tree-with-locs second second meta) =>
   {:path "foo.txt" :start 1000001 :end 1000004}))

;; ## Namespaces and Dependencies

;; In the $y_0$ architecture, the parser is given the name of the module, and
;; is expected to output, alongside the parse-tree (in form of a sequence of
;; statements) a collection of dependency modules.

;; The module name should be embedded in the parser-tree somehow, so that an
;; identifier `foo` written in one module can be distinguished from the same
;; idenfitier written in a different module.

;; In [EDN-based](edn_parser.md) parsers we had the freedom to choose our own
;; syntax, namely the `ns` form at the beginning of each file. This came with
;; the added benefit of allowing the parser to understand its semantics and
;; tell whether a given symbol `foo` is native to this module or is imported
;; from another, updating its namespace in the latter case.

;; Unfortunately, as we turn to parse arbitrary languages, we cannot count on
;; one specific syntax or semantics for importing symbols from modules. This
;; leads to a slightly different architectural choice with regards to the
;; responsibilities of the parser vs. those of the $y_0$ semantic definition
;; of the language.

;; Here, the parser is only responsible for adding the module name to certain
;; types of nodes in the parse-tree, as specified below.

;; As a consequence, a symbol `foo` imported from module `a` to module `b` will
;; be given namespace `a` in module `a` and `b` in module `b`. It is up to the
;; $y_0$ lauguage definition to understand that these two different symbols
;; refer to the same thing.

;; ### Turning Identifiers into Symbols

;; The function `symbolize` takes a parse-tree node, a namespace string and
;; a set of keywords for what counts as "identifiers", and returns the same
;; node, either updated with the identifier string replaced with a symbol, or
;; untouched, if the node is not an identifier.
(fact
 (symbolize [:identifier "bar"]
            "/my/ns.foo" #{:identifier}) => [:identifier 
                                             (symbol "/my/ns.foo" "bar")]
 (symbolize [:foo [:identifier "bar"] [:identifier "baz"]]
            "/my/ns.foo" #{:identifier}) =>
 [:foo [:identifier "bar"] [:identifier "baz"]])

;; As can be seen, the namespace is added as the second element to an identifier
;; node.

;; If the node's keyword appears in the set but has more than one element, an
;; exception is raised.
(fact
 (symbolize [:identifier "bar" "baz"]
            "/my/ns.foo" #{:identifier}) =>
 (throws ":identifier node should contain one element but has 2"))

;; Likewise, if the one element after the keyword is not a string, an exception
;; is thrown as well.
(fact
 (symbolize [:identifier [:foo "bar"]]
            "/my/ns.foo" #{:identifier}) =>
 (throws ":identifier node should contain a single string. Found: [:foo \"bar\"]"))

;; We allow for a set of identifier keywords because some languages have more
;; than one kind of identifier, e.g., ones that start with a small letter vs.
;; ones that start with a capital letter, with different roles in the syntax.

;; In the more common case, where there is only one kind of identifier, a
;; keyword can be provided instead of a set. If this is the case, rather than
;; replacing the string within the node, the symbol replaces the entire node.
(fact
 (symbolize [:identifier "bar"]
            "/my/ns.foo" :identifier) => (symbol "/my/ns.foo" "bar"))

;; When replacing the node with a symbol, the symbol is given the node's meta.
(fact
 (meta (symbolize (with-meta [:identifier "bar"] {:foo :bar})
                  "/my/ns.foo" :identifier)) => {:foo :bar})

;; ### Collecting Dependencies

;; The grammar for the target language should make dependencies very easy to
;; detect in the parse tree.

;; Like identifiers, the names of the dependent modules should be identified
;; in a node with a single element -- a string containing the node's name.

;; `extract-deps` takes:
;; * a node,
;; * an atom holding a collection, to be populated with dependencies,
;; * the designated keyword for marking dependencies,
;; * the absolute path of the enclosing module,
;; * a `resolve` function that resolves module names to absolute paths and
;; * an atom holding a collection of errors, if any.
;;
;; If the node is a dependency, it adds the module's absolute path to the
;; collection and replaces the string (dependency module name) with a symbol,
;; where the namespace is the path of the enclosing module and the name is the
;; path of the dependency.
(fact
 (let [a (atom nil)
       resolve (fn [m] (ok (java.io.File.
                            (str "/" (str/replace m #"\." "/") ".foo"))))]
   ;; This does not add a dependency to the collection.
   (extract-deps [:import [:dep "some.module"] [:identifier "somemod"]]
                 a :dep "/foo/bar.baz" resolve (atom nil)) =>
   [:import [:dep "some.module"] [:identifier "somemod"]]
   @a => nil
   ;; But this does...
   (extract-deps [:dep "some.module"] a :dep "/foo/bar.baz" resolve (atom nil)) =>
   [:dep (symbol "/foo/bar.baz" "/some/module.foo")]
   @a => ["/some/module.foo"]))

;; If the dependency node contains a different number of elements than one, or
;; that one element is not a string, exceptions are thrown.
(fact
 (let [a (atom nil)
       resolve (fn [m] (ok (java.io.File.
                            (str "/" (str/replace m #"\." "/") ".foo"))))]
   (extract-deps [:dep "some.module" "some.other.module"]
                 a :dep "foo.bar" resolve (atom nil)) =>
   (throws ":dep node should contain one element but has 2")
   (extract-deps [:dep [:qname "some" "module"]]
                 a :dep "foo.bar" resolve (atom nil)) =>
   (throws ":dep node should contain a single string. Found: [:qname \"some\" \"module\"]")))

;; If `resolve` fails to resolve the module, the error it returns is added to
;; the errors atom.
(fact
 (let [a (atom nil)
       resolve (fn [m] {:err ["Failed to resolve module" m]})
       errs (atom nil)]
   (extract-deps [:dep "some.module"] a :dep "/foo/bar.baz" resolve errs) =>
   [:dep (symbol "/foo/bar.baz" "some.module")]
   @errs => [["Failed to resolve module" "some.module"]]))

;; ### Numeric Literals

;; In order for semantic definitions written in $y_0$ to be able to understand
;; numerical literals (constants), we them from their string representations
;; to numeric representation.

;; For integers, this is done on nodes with the keyword `:int` and a single
;; string argument.

;; The `convert-int-node` function takes a parse-tree node. If it is of the form
;; `[:int "123"]` it will convert it into a node of the form `[:int 123]`.
(fact
 (convert-int-node [:int "123"]) => [:int 123]
 (convert-int-node [:not-int "123"]) => [:not-int "123"]
 (convert-int-node [:int "123" "456"]) =>
 (throws ":int node should contain one element but has 2"))

;; Similarly, `convert-float-node` converts nodes with the `:float` keyword.
(fact
 (convert-float-node [:float "123.456e+7"]) => [:float 123.456e+7]
 (convert-float-node [:not-float "12.3"]) => [:not-float "12.3"]
 (convert-float-node [:float "123." "456"]) =>
 (throws ":float node should contain one element but has 2"))

;; ## The Parser Function

;; `instaparser` takes:
;; 1. a grammar string,
;; 2. a keyword or a set of keywords representing identifiers,
;; 3. a single keyword representing a dependency module,
;; 4. a list module paths representing _additional_ dependencies (usually used
;;    for the language semantic definition in $y_0$) and returns a `:parse`
;; function which can be placed in a language map.
(fact
 (let [grammar "compilation_unit = import* statement*
                import = <'import'> dep <';'>
                dep = #'[a-z_0-9.]+'
                statement = assign
                assign = identifier <'='> expr <';'>
                identifier = #'[a-zA-Z_][a-zA-Z_0-9]*'
                expr = literal | identifier
                <literal> = int / float
                int = #'-?[1-9][0-9]*'
                float = #'-?[1-9][0-9]*([.][0-9]+)?([eE][+\\-][0-9]+)?'
                --layout--
                layout = #'\\s'+"]
  (def my-parser (instaparser grammar
                              :identifier
                              :dep
                              ["/path/to/y7.y0"])) =>
   #'my-parser)
 my-parser => fn?)

;; Given a module path, the text of the module and a resolver for resolving
;; dependencies, the resulting function returns a status containing the module's
;; `statements`: the parse tree with the top-level label removed, and a set of
;; dependencies.

;; In the statements, identifiers are replaced with symbols and `:int` and
;; `:float` literals are replaced with actual values. Each node in the
;; parse-tree is given a location.
(fact
 (def sample-text1 "import foo.core;
                    import bar.core;

                    a = -3;
                    b = 5.7;
                    x = a;")
 (let [resolve (fn [m] (ok (java.io.File.
                            (str "/" (str/replace m #"\." "/") ".y7"))))
       status (my-parser "/path/to/my-module.y7" sample-text1 resolve)
       {:keys [ok]} status
       [statements deps] ok]
   statements =>
   [[:import [:dep (symbol "/path/to/my-module.y7" "/foo/core.y7")]]
    [:import [:dep (symbol "/path/to/my-module.y7" "/bar/core.y7")]]
    [:statement [:assign (symbol "/path/to/my-module.y7" "a")
                 [:expr [:int -3]]]]
    [:statement [:assign (symbol "/path/to/my-module.y7" "b")
                 [:expr [:float 5.7]]]]
    [:statement
     [:assign (symbol "/path/to/my-module.y7" "x")
      [:expr (symbol "/path/to/my-module.y7" "a")]]]]
   ;; Location of the `a` in `a = -3`
   (-> statements (nth 2) second second meta) => {:path "/path/to/my-module.y7"
                                                  :start 2000037
                                                  :end 4000022}
   deps => ["/bar/core.y7" "/foo/core.y7" "/path/to/y7.y0"]))

;; If a dependency cannot be resolved, the error is propagated as the status.
(fact
 (let [resolve (fn [m] {:err ["Failed to resolve module" m]})
       status (my-parser "/path/to/my-module.y7" sample-text1 resolve)]
   status => {:err ["Failed to resolve module" "foo.core"]}))

;; ### Reporting Parse Errors

;; Parsing errors are reported as `Syntax error`, with the location as `meta`.

;; In the following example, the word `is` is where parsing fails.
(fact
 (def sample-text1 "import foo.core;
                    import bar.core;

                    This is a syntax error.")
 (let [resolve (fn [m] (ok (java.io.File.
                            (str "/" (str/replace m #"\." "/") ".y7"))))
       status (my-parser "/path/to/my-module.y7" sample-text1 resolve)
       {:keys [err]} status]
   err => ["Syntax error"]
   (meta err) => {:path "/path/to/my-module.y7"
                  :start 4000026
                  :end 4000026}))