* [Language Configuration](#language-configuration)
  * [Generic Mechanism](#generic-mechanism)
    * [Default Behavior](#default-behavior)
  * [Generating a Language Map from Config](#generating-a-language-map-from config)
    * [EDN-Based-Language-Config](#edn-based-language-config)
    * [Instaparse-Based-Language-Config](#instaparse-based-language-config)
    * [Controlling Explanation Output](#controlling-explanation-output)
```clojure
(ns y0.config-test
  (:require [midje.sweet :refer [fact => throws provided anything]]
            [y0.config :refer :all]
            [y0.resolvers :refer [exists? getenv]]
            [y0.location-util :refer [encode-file-pos]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

```
# Language Configuration

In order to define a new language, one needs to define a few things:
1. A name for the language.
2. A parser for the new language.
3. A [resolver](resolvers.md) for the language's module system.
4. [optionally] A &y_0& module defining its semantics.

This module defines functions for parsing an EDN-based configuration language
for defining languages for &y_0&.

We start by describing the general mechanism of converting a config to a
[language-map](polyglot_loader.md#module-and-language-representation), usable
by $y_0$'s module system. Then we present the actual features that are
available to language developers. 

## Generic Mechanism

We can generalize the problem as follows: we have a _language config_,
consisting of a map with keywords as keys, containing attributes regarding
the language. Some of these keys are pure _data keys_, containing data (e.g.,
the root namespace for the language), while others are _decision keys_,
containing keywords which choose the type of something, e.g., whether the
parser is `:edn` or `:instaparse`.

The interpretation of the config is done using a _spec_. The spec is a map
which maps all the _decision keywords_ to maps containing all the options
for that decision, mapped into functions. These functions may depend on
other config keys and produce a value for the decision key. This value can
then be used as input for other functions to produce other keys.

The function `resolve-config-val` takes a config-spec, a config and a
keyword. It evaluates the value associated with the keyword based on the spec
and the config.

If the requested keyword is not in the spec, that value is returned from the
config.
```clojure
(fact
 (resolve-config-val {} {:foo 123} :foo) => 123)

```
If the key appears in the spec, the key is treated as a decision key. Its
value in the config is used as a key in the spec to choose a function.

In the following example, `:foo` is a decision key, with options `:bar` and
`:baz`. Each option is mapped to a function, represented as a map with
`:func` holding the function itself and `:args` listing the function's
arguments, which for this example is empty. 
```clojure
(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}
                   :baz {:func (constantly "you chose baz") :args []}}}
       config {:foo :baz}]
   (resolve-config-val spec config :foo)) => "you chose baz")

```
If there are `:args` defined, these are evaluated first and then passed to
the functions.
```clojure
(fact
 (let [spec {:foo {:bar {:func #(str "a=" %1 "; b=" %2) :args [:a :b]}
                   :baz {:func #(str "b=" %1 "; a=" %2) :args [:b :a]}}}
       config {:foo :baz
               :a 1
               :b 2}]
   (resolve-config-val spec config :foo)) => "b=2; a=1")

```
If the key does not exist in the config at all, an exception is thrown.
```clojure
(fact
 (resolve-config-val {} {} :foo) =>
 (throws "Key :foo is not found in the config"))

(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}}}
       config {}]
   (resolve-config-val spec config :foo)) =>
 (throws "Key :foo is not found in the config"))

```
If the option selected by the config for a decision key is not one of the
options in the spec, an exception is thrown.
```clojure
(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}
                   :baz {:func (constantly "you chose baz") :args []}}}
       config {:foo :quux}]
   (resolve-config-val spec config :foo)) =>
 (throws "Key :quux is not found in the spec for :foo"))

```
### Default Behavior

If the spec for a given attribute contains the `:default` key, it provides
the default behavior in case the key is not present in the config.
```clojure
(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}
                   :baz {:func (constantly "you chose baz") :args []}
                   :default {:func (constantly "you did not choose anything")
                             :args []}}}
       config {}]
   (resolve-config-val spec config :foo)) => "you did not choose anything")

```
## Generating a Language Map from Config

The function `language-map-from-config` takes a _language config_, a map
that maps language names into configs for these languages, and returns a
language map which can be used for loading modules in these languages.

### EDN-Based Language Config

The following configuration could be the configuration for $y_1$ (our
[example language](y1.md)). It defines a EDN-based parser with the language's
keywords and a module system that looks for files based on the roots in the
environment variable `Y1_PATH`.
```clojure
(fact
 (let [config {"y1" {;; Use an EDN parser
                     :parser :edn
                     ;; Initialize the root namespace from a list of symbols 
                     :root-refer-map :root-symbols
                     ;; All these symbols...
                     :root-symbols '[defn deftype declfn defclass definstance]
                     ;; ...populate this namespace 
                     :root-namespace "y1.core"
                     ;; The resolver is based on a prefix list
                     :resolver :prefix-list
                     ;; The path relative to the prefixes is based on dots in
                     ;; the module name (e.g., a.b.c => a/b/c.ext)
                     :relative-path-resolution :dots
                     ;; The file extension for the source files
                     :file-ext "y1"
                     ;; The modules that define the language's semantics
                     :extra-modules [{:lang "y0" :name "y1"}]
                     ;; The prefix list comes from an environment variable...
                     :path-prefixes :from-env
                     ;; ...named Y0-PATH
                     :path-prefixes-env "Y1-PATH"
                     ;; Files are read using Clojure's slurp function.
                     :reader :slurp
                     ;; The tree will be decorated with semantic information.
                     ;; This should only be set for languages that the end-user
                     ;; is expected to understand.
                     :decorate :true}}]
   (def lang-map1 (language-map-from-config config)) => #'lang-map1)
   ;; Now we can use parse and see if it works
 (let [{:keys [parse read resolve]} (get lang-map1 "y1")
       parsed (parse "my.module" "/my/module.y1" "(ns my.module (:require [bar])) defn a b")]
   parsed => {:ok '[(y1.core/defn my.module/a my.module/b)
                    ({:lang "y1" :name "bar"}
                     {:lang "y0" :name "y1"})]}
     ;; Because we asked to `:decorate`, `:decorate` is `true` in the
     ;; `lang-map`.
   (-> lang-map1 (get "y1") :decorate) => true
     ;; Checking that we got the resolver we wanted
   (let [path (io/file "./a/b/c.y1")]
     (resolve "a.b.c") => {:ok path}
     (provided
      (exists? path) => true
      (getenv "Y1-PATH") => ".:/foo:/bar"))
     ;; :read is a function that actually (tries to) reads files
   (read "a-path-that-does-not-exist") => (throws java.io.FileNotFoundException)))

```
### Instaparse-Based Language Config

The following example language configuration uses an Instaparse-based parser.
```clojure
(fact
 (let [config {"c0" {;; Use an Instaparser
                     :parser :insta
                     :grammar "compilation_unit = import* statement*
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
                               layout = #'\\s'+"
                     ;; The keyword (or keywords) representing an identifier in
                     ;; the grammar
                     :identifier-kws :identifier
                     ;; The keyword representing a dependency module name in the
                     ;; grammar
                     :dependency-kw :dep
                     ;; Same resolver as in the y1 example above, adapted to c0.
                     :resolver :prefix-list
                     :relative-path-resolution :dots
                     :file-ext "c0"
                     :extra-modules [{:lang "y0" :name "c0"}]
                     :path-prefixes :from-env
                     :path-prefixes-env "C0-PATH"
                     :reader :slurp}}]
   (def lang-map1 (language-map-from-config config)) => #'lang-map1)
 ;; Now we can use parse and see if it works
 (let [{:keys [parse _resolve]} (get lang-map1 "c0")]
   (parse "my.module" "/my/module.c0" "import foo; a = 1; b = 2.3;") =>
   {:ok '[[[:import [:dep my.module/foo]]
           [:statement [:assign my.module/a [:expr [:int 1]]]]
           [:statement [:assign my.module/b [:expr [:float 2.3]]]]]
          [{:lang "c0" :name "foo"}
           {:lang "y0" :name "c0"}]]})
 ;; Because we did not set `:decorate`, `:decorate` is `false` in the
 ;; `lang-map`.
 (-> lang-map1 (get "c0") :decorate) => false)

```
### Controlling Explanation Output

When translating a why-not explanation to a string, there is more than one
way to stringify the parse-tree nodes referenced in the explanation.

The `:stringify-expr` key can control this. If not specified, a
[stringifier intended for EDN-based languages](explanation.md#stringifying-s-expressions)
is used.
```clojure
(fact
 (let [config {"y3" {;; Same as y1 above.
                     :parser :edn
                     :root-refer-map :root-symbols
                     :root-symbols '[defn deftype declfn defclass definstance]
                     :root-namespace "y1.core"
                     :resolver :prefix-list
                     :relative-path-resolution :dots
                     :file-ext "y1"
                     :extra-modules [{:lang "y0" :name "y1"}]
                     :path-prefixes :from-env
                     :path-prefixes-env "Y1-PATH"
                     :reader :slurp}}
       langmap (language-map-from-config config)
       stringify-expr (-> langmap (get "y3") :stringify-expr)]
   (stringify-expr '(x/foo 1 2 3)) => "(foo 1 2 ...)"))

```
However, for languages based on a context-free parser, it is better to show
the text underlying the parse-tree node. Setting `:expr-stringifier` to
`:extract-text` has this effect.
```clojure
(fact
 (let [f (java.io.File/createTempFile "test" ".txt")]
    ;; Write some contents to a temp-file.
   (.deleteOnExit f)
   (spit f (str/join (System/lineSeparator) ["123456789 - 1"
                                             "123456789 - 2"
                                             "123456789 - 3"
                                             "123456789 - 4"
                                             "123456789 - 5"
                                             "123456789 - 6"]))
   (let [config {"y3" {;; Same as y1 above.
                       :parser :edn
                       :root-refer-map :root-symbols
                       :root-symbols '[defn deftype declfn defclass definstance]
                       :root-namespace "y1.core"
                       :resolver :prefix-list
                       :relative-path-resolution :dots
                       :file-ext "y1"
                       :extra-modules [{:lang "y0" :name "y1"}]
                       :path-prefixes :from-env
                       :path-prefixes-env "Y1-PATH"
                       :reader :slurp
                       ;; Stringify by extracting text from the source file
                       :expr-stringifier :extract-text}}
         langmap (language-map-from-config config)
         expr (with-meta [1 2 3] {:path (str f)
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 3 8)})
         stringify-expr (-> langmap (get "y3") :stringify-expr)]
     (stringify-expr expr) => "567")))
```

