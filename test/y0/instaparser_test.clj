(ns y0.instaparser-test
  (:require [midje.sweet :refer [fact => throws]]
            [y0.instaparser :refer :all]))

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

;; ### Adding Namespaces to Identifiers

;; The function `add-namespace` takes a parse-tree node, a namespace string and
;; a set of keywords for what counts as "identifiers", and returns the same
;; node, either updated with a namespace added, or untouched, if the node is not
;; an identifier.
(fact
 (add-namespace [:identifier "bar"]
                "my.ns" #{:identifier}) => [:identifier "bar" "my.ns"]
 (add-namespace [:foo [:identifier "bar"] [:identifier "baz"]]
                "my.ns" #{:identifier}) =>
 [:foo [:identifier "bar"] [:identifier "baz"]])

;; As can be seen, the namespace is added as the second element to an identifier
;; node.

;; If the node's keyword appears in the set but has more than one element, an
;; exception is raised.
(fact
 (add-namespace [:identifier "bar" "baz"]
                "my.ns" #{:identifier}) =>
 (throws ":identifier node should have one element but has 2"))

;; Likewise, if the one element after the keyword is not a string, an exception
;; is thrown as well.
(fact
 (add-namespace [:identifier [:foo "bar"]]
                "my.ns" #{:identifier}) =>
 (throws ":identifier node should contain a single string. Found: [:foo \"bar\"]"))