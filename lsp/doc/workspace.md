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
The keys in `:ms` are full paths to modules files. These same keys act as
nodes in `:mg`.

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
              (add-module {:path "/foo.y1"} identity))]
   (:ms ws) => {"/foo.y1" {:path "/foo.y1"}}
   (nodes (:mg ws)) => #{"/foo.y1"}))

```
If a module has dependencies, they are added as edges in the graph.
```clojure
(fact
 (let [load (fn [m] (if (= (:path m) "/foo.y1")
                      (assoc m :deps ["/bar.y1"
                                      "/baz.y1"])
                      m))
       ws (-> (new-workspace)
              (add-module {:path "/foo.y1"} load))]
   (-> ws :ms keys set) => #{"/foo.y1" "/bar.y1" "/baz.y1"}
   (-> ws :mg nodes) => #{"/foo.y1" "/bar.y1" "/baz.y1"}
   (-> ws :mg (predecessors "/foo.y1")) => #{"/bar.y1" "/baz.y1"}))

```
If a module is already in the workspace, it is not loaded again.

To demonstrate this, we take an empty workspace and call `add-module` twice,
once with the `identity` function as `load`, and once with a function that
throws an exception. The exception is not thrown because the `load` function
is not called the second time around.
```clojure
(fact
 (let [ws (-> (new-workspace)
              (add-module {:path "/foo.y1"} identity)
              (add-module {:path "/foo.y1"}
                          #(throw (Exception.
                                   (str "this should not have been called: "
                                        %1)))))]
   (:ms ws) => {"/foo.y1" {:path "/foo.y1"}}
   (-> ws :mg nodes) => #{"/foo.y1"}))

```
### Example Dependency Graph

In order to create examples in this doc, we will use the following `load`
function. It assumes the `:path` of a module is of the form `/n.y1', where
`n` is a decimal integer and adds `:deps` that correspond to all the dividers
of this integer.
```clojure
(defn- load-dividers [{:keys [path] :as m}]
  (let [[_ num] (re-matches #"/(\d+).y1" path)
        n (Integer/parseInt num)]
    (loop [k (dec n)
           deps nil
           stop #{}]
      (if (= k 0)
        (assoc m :deps deps)
        (cond
          (->> stop (filter #(= (mod % k) 0)) seq) (recur (dec k) deps stop)  
          (= (mod n k) 0) (recur (dec k) 
                                 (conj deps (str "/" k ".y1")) 
                                 (conj stop k)) 
          :else (recur (dec k) deps stop))))))

(fact
 (load-dividers {:path "/12.y1"}) => {:path "/12.y1"
                                      :deps ["/4.y1" "/6.y1"]}
 (let [ws (-> (new-workspace)
              (add-module {:path "/12.y1"} load-dividers))]
   (-> ws :mg nodes) => #{"/1.y1" "/2.y1" "/3.y1" "/4.y1" "/6.y1" "/12.y1"}))

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
 (let [eval-func (fn [ps {:keys [path]}]
                   (update ps :modules #(conj % path)))
       ws (-> (new-workspace)
              (add-module {:path "/12.y1"} load-dividers)
              (eval-with-deps {:path "/6.y1"} eval-func))]
   (-> ws :ms (get "/6.y1") :cache) =>
   {:pre-ps {:modules ["/3.y1" "/2.y1" "/1.y1"]}
    :ps {:modules ["/6.y1" "/3.y1" "/2.y1" "/1.y1"]}
    :all-deps #{"/1.y1" "/2.y1" "/3.y1" "/6.y1"}
    :refs #{"/6.y1"}}
   (-> ws :ms (get "/3.y1") :cache) =>
   {:all-deps #{"/1.y1" "/2.y1" "/3.y1"}
    :pre-ps {:modules ["/2.y1" "/1.y1"]}
    :ps {:modules ["/3.y1" "/2.y1" "/1.y1"]}
    :refs #{"/3.y1" "/6.y1"}}
   (-> ws :ms (get "y1:12") :cache) => nil?))

```
The cache consists of the following keys:

* `:pre-ps` captures the evaluation state before the module.
* `:ps` captures the evaluation state after the module.
* `:all-deps` captures all the modules that contributed to `:ps`, whether
  they are recursive dependencies or not.
* `:refs` are the reciprocal of `:all-deps` such that if `x` is in
  `:all-deps` of `y`, `y` is in the `:refs` of `x`.

In the previous example, `/2.y1` was a member of `:all-deps` for `/3.y1`
despite not being a dependency, because `/2.y1` precedes `/3.y1` in the
topological sort.

### Incremental Updates

When evaluating a module, if some of the dependencies are already evaluated
(i.e., have a `:cache`), it is not necessary to reevaluate all of them.

We can choose a dependency that has a `:cache` (we will call this the
_pivot_) and start the evaluation from its `:ps`. Then, when looking for
modules to evaluate, we omit from the search the pivot's `:all-deps`. This
way we make sure that each module is only evaluted once, either before, as
captued by the cache, or now, but never both.

In the following example we create an example workspace of `12` and, as
before, evaluate module `6`. Then we evaluate module `12` using a different
evaluation function. We notice how `6` and its dependencies are reused from
the first evaluation, while `12` and `4` come from the new evaluation.
```clojure
(fact
 (let [eval-func-before (fn [ps {:keys [path]}]
                          (update ps :before #(conj % path)))
       eval-func-now (fn [ps {:keys [path]}]
                       (update ps :now #(conj % path)))
       ws (-> (new-workspace)
              (add-module {:path "/12.y1"} load-dividers)
              (eval-with-deps {:path "/6.y1"} eval-func-before)
              (eval-with-deps {:path "/12.y1"} eval-func-now))]
   (-> ws :ms (get "/12.y1") :cache :ps) =>
   {:before ["/6.y1" "/3.y1" "/2.y1" "/1.y1"] :now ["/12.y1" "/4.y1"]}))
```

