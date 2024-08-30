* [A Polyglot Module Loader](#a-polyglot-module-loader)
  * [Module and Language Representation](#module-and-language-representation)
    * [Loading Modules](#loading-modules)
```clojure
(ns y0.polyglot-loader-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.polyglot-loader :refer :all]))

```
# A Polyglot Module Loader

$y_0$ requires a polyglot module system. The reason is that modules of
hosted languages are loaded alongside $y_0$ files holding the semantic
definition of the language. The hosted language and $y_0$ may require
different parsers and possibly different resolution logic (logic for finding
files corresponding to a given module on disk), but after parsing, their
trees are used together as a single $y_0$ program.

One example of this idea is $y_1$, which is defined and being used (in
tests) [in the same file](y1.md).

This module defines the functions that make up $y_0$'s polyglot module
system.

## Module and Language Representation

Modules are represented as maps containing the all or part of the following
keys:

1. `:lang` (required), containing the language name, as a string.
2. `:name` (required), containing the module name, as a string.
3. `:path`, containing the path to the module file on disk. Filled if
   already resolved.
4. `:text`, containig the module's contents, as text. Filled after reading
   the file from disk.
5. `:statements`, containing a sequence of parsed statements (parse-tree
   fragments).
6. `:deps`, containing a collection of modules (with at least`:lang` and
   `:name`), representing the module's dependencies.

A language is also represented as a map, with two keys, `:resolve` and
`:parse`. Both function take and return a module. They add keys to the
module they process, as follows:

* `:resolve` assumes `:name` and adds `:path`.
* `:parse` assumes `:name`, `:path` and `:text` and adds `:statements` and
  `:deps`.

The collection of all supported languages is contained in a _language map_,
which maps a language name (string) to its representation.

### Loading Modules

The function `load-module` takes a module (as defined above) with partial
keys and a language map, and completes it.

For the following examples, let us use the following language-map:
```clojure
(def lang-map {"y1" {:resolve #(assoc % :path "some/path")
                     :parse #(-> %
                                 (assoc :statements `[parsing ~(:text %)])
                                 (assoc :deps [{:lang "y1"
                                                :name (str (:name %) 2)}]))}
               "y2" {:resolve #(assoc % :path "some/other/path")
                     :parse #(-> %
                                 (assoc :statements [`(something-else ~(:name %))])
                                 (assoc :deps [{:lang "y2"
                                                :name (str (:name %) 3)}]))}})

```
If it contains `:statements`, it does nothing.
```clojure
(fact
 (load-module {:lang "y1"
               :statements [1 2 ]} lang-map) => {:lang "y1"
                                                :statements [1 2]})

```
If `:statements` is not present, but `:text` is, `:parse` is called.
```clojure
(fact
 (load-module {:lang "y1"
               :name "foo"
               :path "path/to/foo.y1"
               :text "text inside foo.y1"} lang-map)
 => {:lang "y1"
     :name "foo"
     :path "path/to/foo.y1"
     :text "text inside foo.y1"
     :statements `[parsing "text inside foo.y1"]
     :deps [{:lang "y1"
             :name "foo2"}]})

```
If `:text` is not present but `:path` is, `slurp` is called to read the
text, followed by `:parse` to parse it.
```clojure
(fact
 (load-module {:lang "y1"
               :name "foo"
               :path "path/to/foo.y1"} lang-map)
 => {:lang "y1"
     :name "foo"
     :path "path/to/foo.y1"
     :text "text inside foo.y1"
     :statements `[parsing "text inside foo.y1"]
     :deps [{:lang "y1"
             :name "foo2"}]}
 (provided
  (slurp "path/to/foo.y1") => "text inside foo.y1"))

```
If `:path` is not provided, but `:name` is, `:resolve` is called to resolve
the path.
```clojure
(fact
 (load-module {:lang "y1"
               :name "foo"} lang-map)
 => {:lang "y1"
     :name "foo"
     :path "some/path"
     :text "text inside foo.y1"
     :statements `[parsing "text inside foo.y1"]
     :deps [{:lang "y1"
             :name "foo2"}]}
 (provided
  (slurp "some/path") => "text inside foo.y1"))
```

