* [A Polyglot Module Loader](#a-polyglot-module-loader)
  * [Module and Language Representation](#module-and-language-representation)
    * [Loading a Single Module](#loading-a-single-module)
    * [Optional Decoration](#optional-decoration)
  * [Module Store](#module-store)
    * [Topological Sorting of a Module Store](#topological-sorting-of-a-module store)
    * [Evaluating All Modules](#evaluating-all-modules)
```clojure
(ns y0.polyglot-loader-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.polyglot-loader :refer :all]
            [y0.status :refer [ok unwrap-status ->s let-s]]))

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

A language is also represented as a map, with the following keys, each
holding a function:
* `:resolve` takes the value of a module's `:name` and returns the value of
  its `:path`.
* `:read` takes the value of a module's `:path` and returns its `:text`.
* `:parse` takes `:name`, `:path` and `:text` and returns the values of
  `:statements` and `:deps`, as a tuple.

The collection of all supported languages is contained in a _language map_,
which maps a language name (string) to its representation.

### Loading a Single Module

The function `load-module` takes a module (as defined above) with partial
keys and a language map, and completes it.

For the following examples, let us use the following language-map:
```clojure
(defn- my-read1 [path]
  (str "the contents of " path))
(defn- my-read2 [path]
  (str "the other contents of " path))
(def lang-map {"y1" {:resolve (constantly (ok "some/path"))
                     :read my-read1
                     :parse (fn [name _path text]
                              (ok [(with-meta `[parsing ~text] {:foo :bar})
                                   [{:lang "y1"
                                     :name (str name 2)}]]))}
               "y2" {:resolve #(ok % assoc :path "some/other/path")
                     :read my-read2
                     :parse (fn [name _path text]
                              (ok [`[something-else ~text]
                                   [{:lang "y2"
                                     :name (str name 3)}]]))}})

```
If it contains `:statements`, it does nothing.
```clojure
(fact
 (load-module {:lang "y1"
               :statements [1 2]} lang-map) => {:ok {:lang "y1"
                                                     :statements [1 2]}})

```
If `:statements` is not present, but `:text` is, `:parse` is called.
```clojure
(fact
 (load-module {:lang "y1"
               :name "foo"
               :path "path/to/foo.y1"
               :text "text inside foo.y1"} lang-map)
 => {:ok {:lang "y1"
          :name "foo"
          :path "path/to/foo.y1"
          :text "text inside foo.y1"
          :statements `[parsing "text inside foo.y1"]
          :deps [{:lang "y1"
                  :name "foo2"}]}})

```
If `:text` is not present but `:path` is, `slurp` is called to read the
text, followed by `:parse` to parse it.
```clojure
(fact
 (load-module {:lang "y1"
               :name "foo"
               :path "path/to/foo.y1"} lang-map)
 => {:ok {:lang "y1"
          :name "foo"
          :path "path/to/foo.y1"
          :text "the contents of path/to/foo.y1"
          :statements `[parsing "the contents of path/to/foo.y1"]
          :deps [{:lang "y1"
                  :name "foo2"}]}})

```
If `:path` is not provided, but `:name` is, `:resolve` is called to resolve
the path.
```clojure
(fact
 (load-module {:lang "y1"
               :name "foo"} lang-map)
 => {:ok {:lang "y1"
          :name "foo"
          :path "some/path"
          :text "the contents of some/path"
          :statements `[parsing "the contents of some/path"]
          :deps [{:lang "y1"
                  :name "foo2"}]}})

```
### Optional Decoration

Decorations are meta properties added to the nodes of a parse-tree containing
semantic information. In order for the analysis to update the decorations as
it walks through the tree, empty decorations are added before the analysis,
and the analysis updates them in-place. For in-place updates to be possible,
the decorations consist of atoms.

In the polyglot loader, the decorations are added only for languages that
explicitly request them. This is done to make sure that semantic links are
only drawn between code artifacts that are "interesting" to the end user and
do not link to the $y_0$ definition of the language. Therefore, in settings
where the a single language is defined over $y_0$, that language will have
decorations and $y_0$ will not.

If the language-map entry for a language contains the key `:decorate`, and if
its value is truthy, decorations are added to the tree. `:decorate` is a
Boolean flag rather than a function because the nature of the decorations
themselves is constant and does not change from one language to another.

First, let us see that by default we get no decorations.
```clojure
(fact
 (let-s [m (load-module {:lang "y1"
                         :name "foo"
                         :path "path/to/foo.y1"
                         :text "text inside foo.y1"} lang-map)]
        (do
          (-> m :statements) => `[parsing "text inside foo.y1"]
          (-> m :statements meta) => {:foo :bar})))

```
Now, let us repeat the same example, but after updating `lang-map` so that
for language `y1`, `:decorate` would be `true`.
```clojure
(fact
 (let-s [lang-map (ok (update lang-map "y1" #(assoc % :decorate true)))
         m (load-module {:lang "y1"
                         :name "foo"
                         :path "path/to/foo.y1"
                         :text "text inside foo.y1"} lang-map)]
        (do
          (-> m :statements) => `[parsing "text inside foo.y1"]
          (-> m :statements meta :foo) => :bar
          (-> m :statements meta :matches deref) => {}
          (-> m :statements meta :refs deref) => #{})))

```
As can be seen, the existing meta elements (`:foo`, in this case) still
exist, but two keys were added, both containing atoms. `:matches` is a map
containing information about predicates that match this node. `:refs` is a
set of nodes that reference this node.

The decorations are added throughout the tree, to all nodes that can have
`meta` properties.

In the following example we demonstrate that decorations are added also to
the symbol `parsing` inside the top-level list.
```clojure
(fact
 (let-s [lang-map (ok (update lang-map "y1" #(assoc % :decorate true)))
         m (load-module {:lang "y1"
                         :name "foo"
                         :path "path/to/foo.y1"
                         :text "text inside foo.y1"} lang-map)]
        (do
          (-> m :statements first) => `parsing
          (-> m :statements first meta :matches deref) => {}
          (-> m :statements first meta :refs deref) => #{})))

```
## Module Store

A module ID is a string of the form `land:name`. The function `module-id`
takes a module (map) and returns an ID (string).
```clojure
(fact
 (module-id {:lang "foo" :name "bar"}) => "foo:bar")

```
A module-store is a map, mapping module IDs to the respective completed
modules. `load-with-deps` takes a list of (partial) modules and a
language-map and returns a module-store containing the original modules along
with all their dependencies.

To demonstrate it, we start by building a dependency map of modules.
```clojure
(def my-deps
  {"m1" []
   "m2" ["m1"]
   "m3" ["m1"]
   "m4" ["m2"]
   "m5" ["m1"]
   "m6" ["m3" "m2"]
   "m7" ["m1"]
   "m8" ["m4"]
   "m9" ["m3"]
   "m10" ["m5" "m2"]
   "m11" ["m1"]
   "m12" ["m6" "m4"]})

```
Yes, it was a bit nurdy/lazy of me, but instead of using real names I just
named my modules after natural numbers from 1 to 12 and gave each number as
dependencies the modules with the numbers of the divisors of that number,
omitting devisors of those devisors. For example, for `m12` I wrote `m6` and
`m4`, but not `m3`, because it is already a divisor (dependency) of `m6`.

OK, so after this completely unnecessary detour in middle-school math, we
continue to defining our language-map. We will only have one language here,
`y2`. The resolver will be trivial, matching module `foo` with path
`/foo.y2`. The parser will provide fixed `:statements`, but will provide
dependencies from `my-deps`.
```clojure
(def lang-map2
  {"y2" {:resolve (fn [name]
                    (ok (str "/" name ".y2")))
         :read (fn [path]
                 (str "text in " path))
         :parse (fn [name path text]
                  (ok [[name text]
                       (for [dep (my-deps name)]
                         {:lang "y2"
                          :name dep})]))}})

```
Now we have what we need to call `load-with-deps`.
```clojure
(fact
 (def my-mstore (unwrap-status (load-with-deps [{:lang "y2" :name "m12"}] lang-map2)))
 => #'my-mstore
 my-mstore => {"y2:m12" {:lang "y2"
                         :name "m12"
                         :path "/m12.y2"
                         :text "text in /m12.y2"
                         :statements ["m12" "text in /m12.y2"]
                         :deps [{:lang "y2" :name "m6"}
                                {:lang "y2" :name "m4"}]}
               "y2:m6" {:lang "y2"
                        :name "m6"
                        :path "/m6.y2"
                        :text "text in /m6.y2"
                        :statements ["m6" "text in /m6.y2"]
                        :deps [{:lang "y2" :name "m3"}
                               {:lang "y2" :name "m2"}]}
               "y2:m4" {:lang "y2"
                        :name "m4"
                        :path "/m4.y2"
                        :text "text in /m4.y2"
                        :statements ["m4" "text in /m4.y2"]
                        :deps [{:lang "y2" :name "m2"}]}
               "y2:m3" {:lang "y2"
                        :name "m3"
                        :path "/m3.y2"
                        :text "text in /m3.y2"
                        :statements ["m3" "text in /m3.y2"]
                        :deps [{:lang "y2" :name "m1"}]}
               "y2:m2" {:lang "y2"
                        :name "m2"
                        :path "/m2.y2"
                        :text "text in /m2.y2"
                        :statements ["m2" "text in /m2.y2"]
                        :deps [{:lang "y2" :name "m1"}]}
               "y2:m1" {:lang "y2"
                        :name "m1"
                        :path "/m1.y2"
                        :text "text in /m1.y2"
                        :statements ["m1" "text in /m1.y2"]
                        :deps []}})

```
### Topological Sorting of a Module Store

Given a module-store, we wish to find an order in which we could evaluate
modules, so that all dependencies of a given module are evaluated before it.

This requires us to topologically-sort a graph, in which the nodes are
modules and the edges are dependencies.

A topological sort of a graph starts with source nodes. These are nodes that
do not have any edge go into them. In our case, these are the modules that
have no dependencies.

`mstore-sources` takes a module-store and returns a set of source module
IDs.
```clojure
(fact
 (mstore-sources my-mstore) => #{"y2:m1"})

```
Next, the algorithm needs to walk from these sources to all the nodes
reachable from them through forward edges, listing all the nodes it
encounters in-order. Unfortunately, `:deps` provides _backward_ edges. To
fix this, `mstore-refs` generates an inverse index of `:deps`, creating a
map from module-IDs to the IDs of modules that directly depend on them.
```clojure
(fact
 (mstore-refs my-mstore) => {"y2:m1" #{"y2:m2" "y2:m3"}
                             "y2:m12" #{}
                             "y2:m2" #{"y2:m4" "y2:m6"}
                             "y2:m3" #{"y2:m6"}
                             "y2:m4" #{"y2:m12"}
                             "y2:m6" #{"y2:m12"}})

```
`mstore-toposort` does the actual sorting. It takes a module-store and
returns a sequence of module-IDs, given in some topological order.
```clojure
(fact
 (mstore-toposort my-mstore) => ["y2:m1" "y2:m2" "y2:m4" "y2:m3" "y2:m6" "y2:m12"])

```
Please note that the order above is only one possible sort (`m2` can come
before `m3` and `m4` can come before `m6`), but Clojure has stable
representations for maps and sets, making it possible to write a test with
one particular result. Please note that this particular result is
topologically sorted. `m1` comes first, `m12` comes last; `m3` and `m2`
precede `m6`, etc.

### Evaluating All Modules

The state of a $y_0$ program, along with code in hosted languages is stored
as a [predstore](predstore.md). This is a map containing predicates, rules
and statements that are computed from the `:statements` in the different
modules.

`eval-mstore` takes a module-store, an evaluation function and an initial
predstore, and _evaluates_ it. It
[topologically sorts](#topological-sorting-of-a-module-store) the modules
and evaluates them one by one, by calling the evaluation function.

It returns the same module-store, adding the key `:predstore` in each
module, containing the predstore as seen at the end of this module.

To demonstrate this, we use the following fake function as the evaluation
function.
```clojure
(defn fake-eval-statements [ps statements]
  (ok ps update :foo #(concat % statements)))

```
This function maintains a single entry in the predstore, `:foo`, and appends
the statements to it. For example:
```clojure
(fact
 (->s (ok {})
      (fake-eval-statements [1 2 3])
      (fake-eval-statements [4 5])
      (fake-eval-statements [6 7 8]))
 => {:ok {:foo [1 2 3 4 5 6 7 8]}})

(fact
 (let-s [mstore (eval-mstore my-mstore fake-eval-statements {:bar :baz})]
        (do
          (-> mstore (get "y2:m12") :predstore :foo) =>
          ["m1" "text in /m1.y2"
           "m2" "text in /m2.y2"
           "m4" "text in /m4.y2"
           "m3" "text in /m3.y2"
           "m6" "text in /m6.y2"
           "m12" "text in /m12.y2"]
          (-> mstore (get "y2:m12") :predstore :bar) => :baz)))
```

