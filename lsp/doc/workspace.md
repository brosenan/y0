```clojure
(ns y0lsp.workspace-test
  (:require [midje.sweet :refer [fact =>]]
            [y0lsp.workspace :refer :all]
            [loom.graph :refer [graph? directed? nodes predecessors]]))

```
# Workspace

In IDEs in general, a workspace refers to the space in which source files and
their dependencies can be found, typically a directory in a file system. The
LSP has a [similar
notion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceFeatures).

In the `y0lsp`, the workspace is a set of modules that are currently loaded.
These consist of _principal modules_, which are explicitly opened by the
client, and _dependency modules_, which are only opened in order to provide
definitions to principal modules.

The workspace maintains a dependency graph of these modules, and tracks
changes to it. Whenever a notification about an update to a module is
received, the workspace is updated accordingly, by reading the new module and
reevaluating it and any other principle module that depends on it.

The workspace maintains caches to avoid repetitive evaluations. This caching
requires bookkeeping to make evaluation is made on fresh data.

In the following sections we build this mechanism from the ground up.

We are going to rely heavily on the
[polyglot_loader](../../doc/polyglot_loader.md) which is a part of the core
$y_0$ implementation.

## Data Structure

A workspace is a map with two keys: `:ms`, holding a [module
store](../../doc/polyglot_loader.md#module-store), and `:mg`, holding a
_module graph_, a [loom](https://cljdoc.org/d/aysylu/loom/1.0.2/doc/readme)
`digraph` containing the dependencies.

`new-workspace` returns a new (empty) workspace.
```clojure
(fact
 (let [ws (new-workspace)]
   (:ms ws) => {}
   (:mg ws) => graph?
   (:mg ws) => directed?))

```
The keys in `:ms` are _module keys_, strings of the form `lang:name`. These
same keys act as nodes in `:mg`.

## Adding a Module

`add-module` takes a workspace, a
[module](../../doc/polyglot_loader.md#module-and-language-representation),
and a `load` function to [load the
module](../../doc/polyglot_loader.md#loading-a-single-module), and returns
the workspace with this module added.

In the following example we take an empty environment and add a simple module
to it. We will use the `identity` function as the `load` function, since we
do not need to add anything to the module for this example. Specifically, we
do not add any `:deps` at this point. The result will be that the module will
be added to the `:ms` as a value and to the `:mg` as a node.
```clojure
(fact
 (let [ws (-> (new-workspace)
              (add-module {:lang "y1" :name "foo"} identity))]
   (:ms ws) => {"y1:foo" {:lang "y1" :name "foo"}}
   (nodes (:mg ws)) => #{"y1:foo"}))

```
If a module has dependencies, they are added as edges in the graph.
```clojure
(fact
 (let [load (fn [m] (if (= (:name m) "foo")
                      (assoc m :deps [{:lang "y1" :name "bar"}
                                      {:lang "y1" :name "baz"}])
                      m))
       ws (-> (new-workspace)
              (add-module {:lang "y1" :name "foo"} load))]
   (-> ws :ms keys set) => #{"y1:foo" "y1:bar" "y1:baz"}
   (-> ws :mg nodes) => #{"y1:foo" "y1:bar" "y1:baz"}
   (-> ws :mg (predecessors "y1:foo")) => #{"y1:bar" "y1:baz"}))

```
If a module is already in the workspace, it is not loaded again.

To demonstrate this, we take an empty workspace and call `add-module` twice,
once with the `identity` function as `load`, and once with a function that
throws an exception. The exception is not thrown because the `load` function
is not called the second time around.
```clojure
(fact
 (let [ws (-> (new-workspace)
              (add-module {:lang "y1" :name "foo"} identity)
              (add-module {:lang "y1" :name "foo"}
                          #(throw (Exception.
                                   (str "this should not have been called: "
                                        %1)))))]
   (:ms ws) => {"y1:foo" {:lang "y1" :name "foo"}}
   (-> ws :mg nodes) => #{"y1:foo"}))

```
### Example Dependency Graph

In order to create examples in this doc, we will use the following `load`
function. It assumes the `:name` of a module is a decimal integer and adds
`:deps` that correspond to all the dividers of this integer.
```clojure
(defn- load-dividers [{:keys [name lang] :as m}]
  (let [n (Integer/parseInt name)]
    (loop [k (dec n)
           deps nil
           stop #{}]
      (if (= k 0)
        (assoc m :deps deps)
        (cond
          (->> stop (filter #(= (mod % k) 0)) seq) (recur (dec k) deps stop)  
          (= (mod n k) 0) (recur (dec k) 
                                 (conj deps {:lang lang :name (str k)}) 
                                 (conj stop k)) 
          :else (recur (dec k) deps stop))))))

(fact
 (load-dividers {:lang "y1" :name "12"}) => {:lang "y1"
                                             :name "12"
                                             :deps [{:lang "y1" :name "4"}
                                                    {:lang "y1" :name "6"}]}
 (let [ws (-> (new-workspace)
              (add-module {:lang "y1" :name "12"} load-dividers))]
   (-> ws :mg nodes) => #{"y1:1" "y1:2" "y1:3" "y1:4" "y1:6" "y1:12"}))

```
## Caching

The workspace uses caching of evaluation state to facilitate fast response to
file updates. Ideally, if the dependencies of a module did not change, we
don't need to reevaluate them, just re-evaluate the module itself. If some
dependencies were added or removed, however, the evaluation state needs to be
recomputed. But still, some state can still be reused so that recomputation
does not have to take place from the very beginning.

In this section we discuss how caching works, and in particular, how we make
sure that cached data is up-to-date.

### Background

Before we dive into the caching mechanism, a bit of background. $y_0$
evaluation state is modeled as a [Predstore](../../doc/predstore.md), or `ps`
for short. This is a data structure that contains all the definitions that
are in effect at a certain point of the evaluation.

A module _evaluation_ is a process that starts with the parse-tree of a given
module and the `ps` prior to its evaluation (this should already have to
include all its dependencies), and results in an updated `ps`, which
typically encodes all the definitions made by the module.

This process requires a total order of modules to be selected. While the
modules lay out a general DAG (dependency graph), their evaluation is made in
a chain, where each module has at most one module directly preceding it and
at most one module directly succeeding it.

This chain is required to be a [topological
sort](https://en.wikipedia.org/wiki/Topological_sorting) of the dependency
graph.

### Evaluation and Caching

The function `eval-with-deps` takes a workspace, a module and an evaluation
function and returns the updated workspace, populating `:cache` entries in
the module and its dependencies.

To demonstrate this we take the [example graph](#example-dependency-graph)
of `12` and evauate the `6` node. The evaluation function simply adds the
modules it "evaluates" to a list named `:modules` in the `ps`.

We check that both `6` and all of its dependencies (we check `2` as an
example) have a `:cache` entry, but `12` doesn't.
```clojure
(fact
 (let [eval-func (fn [ps {:keys [name]}]
                   (update ps :modules #(conj % name)))
       ws (-> (new-workspace)
              (add-module {:lang "y1" :name "12"} load-dividers)
              (eval-with-deps {:lang "y1" :name "6"} eval-func))]
   (-> ws :ms (get "y1:6") :cache) =>
   {:pre-ps {:modules ["2" "3" "1"]}
    :ps {:modules ["6" "2" "3" "1"]}
    :all-deps #{"y1:1" "y1:2" "y1:3" "y1:6"}}
   (-> ws :ms (get "y1:2") :cache) =>
   {:pre-ps {:modules ["3" "1"]}
    :ps {:modules ["2" "3" "1"]}
    :all-deps #{"y1:1" "y1:2" "y1:3"}}
   (-> ws :ms (get "y1:12") :cache) => nil?))

```
The cache consists of the following keys:

* `:pre-ps` captures the evaluation state before the module.
* `:ps` captures the evaluation state after the module.
* `:all-deps` captures all the modules that contributed to `:ps`, whether
  they are recursive dependencies or not.

In the previous example, `y1:3` was a member of `:all-deps` for `y1:2`
despite not being a dependency, because `y1:3` precedes `y1:2` in the
topological sort.
