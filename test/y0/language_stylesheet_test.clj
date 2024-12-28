(ns y0.language-stylesheet-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.language-stylesheet :refer :all]))

;; # Language Stylesheet

;; The result of the evaluation of a $y_0$ program on a given parse-tree is
;; [decorations](rules.md#tracing-definitions) on that tree. These decorations
;; contain all the semantic information that can be derived from the $y_0$
;; definition of the language, which is hopefully enough to provide all the
;; semantic services a language server can provide.

;; However, there is an gap between the decorations and providing language
;; services for a given language. The decorations are written in terms of the
;; predicates used in the semantic definition, which are different predicates
;; for different languages. The language server protocol (LSP), however, speaks
;; in general terms, which are, of course, different.

;; This module provides a language for reconciling between the two. This
;; CSS-inspired language attaches _attributes_ to decorated parse-tree nodes,
;; similar to how CSS provides attributes to HTML elements.

;; In our CSS metaphor, the type of a parse-tree node (typically, the symbol or
;; keyword at the beginning of the list or vector that represents it),
;; corresponds to an HTML element type and predicates matching a node correspond
;; to classes assigned to a HTML element.

;; In the following sections we define the semantics of the language stylesheet
;; language step by step.

;; ## Selectors

;; A _selector_ is a keyword or a symbol that represent filters on decorated
;; parse-tree nodes, which are represented with their names (symbol or keyword)
;; and a set of $y_0$ predicate names (strings) that matched them.

;; The function `selector-to-func` takes a selector and returns a function
;; (predicate) which takes a name and a set of predicate names and return
;; whether or not they match the selector.

;; A simple keyword selector represents the same keyword.
(fact
 (let [f (selector-to-func :foo)]
   (f :foo #{}) => true
   (f :bar #{}) => false
   (f 'foo #{}) => false))

;; Similarly, a simple symbol matches the same symbol, including namespace.
(fact
 (let [f (selector-to-func 'foo/bar)]
   (f 'foo/bar #{}) => true
   (f 'foo1/bar #{}) => false
   (f 'foo/bar2 #{}) => false
   (f :foo/bar #{}) => false))

;; ### Matched Predicates

;; The selector may contain suffixes of the form `.pred`, where `pred` is a name
;; of a $y_0$ predicate, excluding its namesapce. The selector matches if all
;; the specified `pred`s are in the given set of predicate names.
(fact
 (let [f (selector-to-func 'foo/bar.baz.quux)]
   (f 'foo/bar #{"baz" "quux"}) => true
   (f 'foo/bar #{"baz"}) => false
   (f 'foo/bar1 #{"baz" "quux"}) => false))

(fact
 (let [f (selector-to-func :bar.baz.quux)]
   (f :bar #{"baz" "quux"}) => true
   (f :bar #{"baz"}) => false
   (f :bar1 #{"baz" "quux"}) => false))

;; ## The Language

;; A language stylesheet is a vector consisting of a _default block_, defining
;; default attribute values, followed by pairs of [selectors](#selectors) and
;; _declaration blocks_ (maps), forming _rules_ which define attribute values
;; for specific cases.

;; The function `compile-stylesheet` takes a stylesheet and returns a function
;; which represents it. The "compiled stylesheet" function takes a name of a
;; parse-tree node (symbol or keyword), a set of strings representing names
;; of matched predicate (omitting their namespaces) and a keyword representing
;; an attribute. It returns the value of that attribute for this node based on
;; the stylesheet.

;; If the attribute is not defined, `nil` is returned.
(fact
 (let [f (compile-stylesheet [{}])]
   (f  :foo #{} :my-attr)) => nil)

;; If the attribute is defined in the default block, and no other rule matches,
;; the default value is returned.
(fact
 (let [f (compile-stylesheet [{:my-attr 42}])]
   (f :foo #{} :my-attr)) => 42)

;; ### Stylesheet Rules

;; A _rule_ consists of a selector and a declaration block (map). If a selector
;; matches the given node-name and predicate names and its corresponding
;; declaration block defines a value for the requested attribute, this value
;; takes priority over the default.
(fact
 (let [f (compile-stylesheet [{:my-attr 42}
                              :foo.bar {:my-attr 43}])]
   (f :foo #{"bar" "baz"} :my-attr)) => 43)

;; If more than one rule matches, the last takes precedence.
(fact
 (let [f (compile-stylesheet [{:my-attr 42}
                              :foo.bar {:my-attr 43}
                              :foo.bar.baz {:my-attr 44}])]
   (f :foo #{"bar" "baz"} :my-attr)) => 44)

;; Rules that do not provide a value for the given attribute are ignored, even
;; if they are more specific.
(fact
 (let [f (compile-stylesheet [{:my-attr 42}
                              :foo.bar {:my-attr 43}
                              :foo.bar.baz {:some-other-attr 88}])]
   (f :foo #{"bar" "baz"} :my-attr)) => 43)
