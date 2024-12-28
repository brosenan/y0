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
;; which represents it. The "compiled stylesheet" function takes a decorated
;; parse-tree node and a keyword representing an attribute. It returns the value
;; of that attribute for this node based on the stylesheet.

;; If the attribute is not defined, `nil` is returned.
(fact
 (let [node (with-meta [:foo 1 2 3] {:matches (atom {})})
       f (compile-stylesheet [{}])]
   (f node :my-attr)) => nil)

;; If the attribute is defined in the default block, and no other rule matches,
;; the default value is returned.
(fact
 (let [node (with-meta [:foo 1 2 3] {:matches (atom {})})
       f (compile-stylesheet [{:my-attr 42}])]
   (f node :my-attr)) => 42)

;; ### Stylesheet Rules

;; A _rule_ consists of a selector and a declaration block (map). If a selector
;; matches the given node-name and predicate names and its corresponding
;; declaration block defines a value for the requested attribute, this value
;; takes priority over the default.
(fact
 (let [node (with-meta [:foo 1 2 3] {:matches (atom {'my-ns/bar {}
                                                     'my-ns/baz {}})})
       f (compile-stylesheet [{:my-attr 42}
                              :foo.bar {:my-attr 43}])]
   (f node :my-attr)) => 43)

;; If more than one rule matches, the last takes precedence.
(fact
 (let [node (with-meta [:foo 1 2 3] {:matches (atom {'my-ns/bar {}
                                                     'my-ns/baz {}})})
       f (compile-stylesheet [{:my-attr 42}
                              :foo.bar {:my-attr 43}
                              :foo.bar.baz {:my-attr 44}])]
   (f node :my-attr)) => 44)

;; Rules that do not provide a value for the given attribute are ignored, even
;; if they are more specific.
(fact
 (let [node (with-meta [:foo 1 2 3] {:matches (atom {'my-ns/bar {}
                                                     'my-ns/baz {}})})
       f (compile-stylesheet [{:my-attr 42}
                              :foo.bar {:my-attr 43}
                              :foo.bar.baz {:some-other-attr 88}])]
   (f node :my-attr)) => 43)

;; ### Attribute Value Language

;; Attribute values appearing in declaration blocks are written in a simple
;; expression language which allows content from the parse-tree node to be used
;; in the returned expression.

;; The function `eval-attr` is responsible for this. It takes an attribute value
;; expression and a decorated parse-tree node as input, and returns the
;; evaluated value of that attribute.

;; For "simple" values, the values are returned unchanged.
(fact
 (eval-attr 42 [:my-node 1 2 3]) => 42)

;; The form `with-pred` replaces symbols or keywords in its body with predicate
;; attributes.
(fact
 (let [node (with-meta [:my-node 1 2 3]
              {:matches (atom {'y1/typeof {:args ['y1/int]}})})]
   (eval-attr '(with-pred [y1/typeof :type]
                 ["Type:" :type]) node)) => ["Type:" 'y1/int])

;; The form `with-node` replaces symbols or keywords with arguments of the tree
;; node.
(fact
 (let [node [:my-node 1 2 3]]
   (eval-attr '(with-node [:a :b]
                 ["A:" :a "\nB:" :b]) node)) => ["A:" 1 "\nB:" 2])

;; The form `str` turns a list or a vector into a string.
(fact
 (let [node [:my-node 1 2 3]]
   (eval-attr '(str ["Hello" "world"]) node)) => "Hello world")

;; It uses [explanation-to-str](explanation.md#stringifying-explanations) so
;; that tree nodes are stringified properly.
(fact
 (eval-attr '(str ["Hello" [:world 1 2 3]]) [:my-node 1 2 3]) =>
 "Hello [:world 1 2 ...]")

;; The forms discussed here can appear anywhere in the expression.
(fact
 (let [node (with-meta [:my-node 1 2 3]
              {:matches (atom {'y1/typeof {:args ['y1/int]}})})]
   (eval-attr '[(with-pred [y1/typeof :type]
                  ["Type:" :type])
                (with-node [:a :b :c]
                  ["A:" :a ", B:" :b ", C:" :c])
                (str ["Hello" "world"])] node)) => [["Type:" 'y1/int]
                                                    ["A:" 1 ", B:" 2 ", C:" 3]
                                                    "Hello world"])

;; They can also be combined.
(fact
 (let [node (with-meta [:my-node 1 2 3]
              {:matches (atom {'y1/typeof {:args ['y1/int]}})})]
   (eval-attr '(str (with-pred [y1/typeof :type]
                      (with-node [:a :b]
                        ["Type:" :type "\n"
                         "A:" :a "\n"
                         "B:" :b "\n"]))) node)) =>
 "Type: int \n A: 1 \n B: 2 \n")
