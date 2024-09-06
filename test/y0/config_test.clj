(ns y0.config-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.config :refer :all]))

;; # Language Configuration

;; In order to define a new language, one needs to define a few things:
;; 1. A name for the language.
;; 2. A parser for the new language.
;; 3. A [resolver](resolvers.md) for the language's module system.
;; 4. [optionally] A &y_0& module defining its semantics.

;; This module defines functions for parsing an EDN-based configuration language
;; for defining languages for &y_0&.

;; We start by describing the general mechanism of converting a config to a
;; [language-map](polyglot_loader.md#module-and-language-representation), usable
;; by $y_0$'s module system. Then we present the actual features that are
;; available to language developers. 

;; ## Generic Mechanism

;; We can generalize the problem as follows: we have a _language config_,
;; consisting of a map with keywords as keys, containing attributes regarding
;; the language. Some of these keys are pure _data keys_, containing data (e.g.,
;; the root namespace for the language), while others are _decision keys_,
;; containing keywords which choose the type of something, e.g., whether the
;; parser is `:edn` or `:instaparse`.

;; The interpretation of the config is done using a _spec_. The spec is a map
;; which maps all the _decision keywords_ to maps containing all the options
;; for that decision, mapped into functions. These functions may depend on
;; other config keys and produce a value for the decision key. This value can
;; then be used as input for other functions to produce other keys.

;; The function `resolve-config-val` takes a config-spec, a config and a
;; keyword. It evaluates the value associated with the keyword based on the spec
;; and the config.

;; If the requested keyword is not in the spec, that value is returned from the
;; config.
(fact
 (resolve-config-val {} {:foo 123} :foo) => 123)

;; If the key appears in the spec, the key is treated as a decision key. Its
;; value in the config is used as a key in the spec to choose a function.

;; In the following example, `:foo` is a decision key, with options `:bar` and
;; `:baz`. Each option is mapped to a function, represented as a map with
;; `:func` holding the function itself and `:args` listing the function's
;; arguments, which for this example is empty. 
(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}
                   :baz {:func (constantly "you chose baz") :args []}}}
       config {:foo :baz}]
   (resolve-config-val spec config :foo)) => "you chose baz")

;; If there are `:args` defined, these are evaluated first and then passed to
;; the functions.
(fact
 (let [spec {:foo {:bar {:func #(str "a=" %1 "; b=" %2) :args [:a :b]}
                   :baz {:func #(str "b=" %1 "; a=" %2) :args [:b :a]}}}
       config {:foo :baz
               :a 1
               :b 2}]
   (resolve-config-val spec config :foo)) => "b=2; a=1")

;; If the key does not exist in the config at all, an exception is thrown.
(fact
 (resolve-config-val {} {} :foo) =>
 (throws "Key :foo is not found in the config"))

(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}}}
       config {}]
   (resolve-config-val spec config :foo)) =>
 (throws "Key :foo is not found in the config"))

;; If the option selected by the config for a decision key is not one of the
;; options in the spec, an exception is thrown.
(fact
 (let [spec {:foo {:bar {:func (constantly "you chose bar") :args []}
                   :baz {:func (constantly "you chose baz") :args []}}}
       config {:foo :quux}]
   (resolve-config-val spec config :foo)) =>
 (throws "Key :quux is not found in the spec for :foo"))