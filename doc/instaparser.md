* [Instaparse Parser](#instaparse-parser)
  * [Instaparse Grammars](#instaparse-grammars)
```clojure
(ns y0.instaparser-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.instaparser :refer :all]))

```
# Instaparse Parser

[Instaparse](https://github.com/Engelberg/instaparse) is a Clojure parser
generator library. It offers a rich language for defining context-free
grammars focusing on accepting a wide variety of syntaxes (e.g., dealing with
ambiguity) as well as performance.

While this is not a part of the $y_0$ language, we choose to offer this
link to Instaparse as part of the library, in order to allow codeless
definitions of languages that can be defined based on this feature.

## Instaparse Grammars

The function `instaparse-grammar` takes a string and returns an Instaparse
parser.
```clojure
(fact
 (let [parser (instaparse-grammar "S = AB;
                                   AB = A B
                                   A = 'a'+
                                   B = 'b'+")]
   (parser "aabb") => [:S [:AB [:A "a" "a"] [:B "b" "b"]]]))
   
   
```
By default, Instaparse is a
[scannerless](https://en.wikipedia.org/wiki/Scannerless_parsing) parser
generator. This means that the generated parser works its way directly from
the level of characters in the input into a parse-tree, skipping the
tokenization phase, which exists in more traditional stacks (such as
Lex+Yacc).

Scannerless parsing is more powerful than scanner-based parsing, but it does
raise the need for accounting for whitespace and comments (which we will
commonly refer to as _layout_) practically everywhere in the grammar.

Instaparse supports this through their
[Auto Whitespace](https://github.com/Engelberg/instaparse/blob/master/docs/ExperimentalFeatures.md#auto-whitespace)
experimental feature. Because we believe this feature is useful for most
grammar-based programming languages, we add built-in support for it.

To support this, a grammar definition may consist of two parts. The first is
the main grammar. Then, if the separator `--layout--` is present, what
follows is interpreted as the grammar for the layout, which must start with
the symbol `layout`.
```clojure
(fact
 (let [parser (instaparse-grammar "S = AB;
                                   AB = A B
                                   A = 'a'+
                                   B = 'b'+
                                   --layout--
                                   layout = #'\\s'+")]
   (parser "a a bb") => [:S [:AB [:A "a" "a"] [:B "b" "b"]]]))
```

