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

`new-workspace` takes two functions (`load-module` and `eval-module`) and
returns a new (empty) workspace. In the following example we provide keywords
instead of actual functions.
```clojure
(fact
 (let [ws (new-workspace :load :eval)]
   (:ms ws) => {}
   (:mg ws) => graph?
   (:mg ws) => directed?
   (:load-module ws) => :load
   (:eval-module ws) => :eval))

```
The keys in `:ms` are full paths to modules files. These same keys act as
nodes in `:mg`.

## Adding a Module

`add-module` takes a workspace and a
[module](../../doc/polyglot_loader.md#module-and-language-representation),
and returns the workspace with this module added.

In the following example we take an empty environment and add a simple module
to it. We will use the `identity` function as the `load` function, since we
do not need to add anything to the module for this example. Specifically, we
do not add any `:deps` at this point. The result will be that the module will
be added to the `:ms` as a value and to the `:mg` as a node.
```clojure
(fact
 (let [ws (-> (new-workspace identity identity)
              (add-module {:path "/foo.y1"}))]
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
       ws (-> (new-workspace load identity)
              (add-module {:path "/foo.y1"}))]
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
 (let [ws (-> (new-workspace identity identity)
              (add-module {:path "/foo.y1"})
              (assoc :load-module #(throw (Exception.
                                           (str "this should not have been called: "
                                                %1))))
              (add-module {:path "/foo.y1"}))]
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
        n (Integer/parseInt num)
        calc-deps (fn [k deps stop]
                    (if (= k 0)
                      deps
                      (cond
                        (->> stop (filter #(= (mod % k) 0)) seq) (recur (dec k) deps stop)
                        (= (mod n k) 0) (recur (dec k)
                                               (conj deps (str "/" k ".y1"))
                                               (conj stop k))
                        :else (recur (dec k) deps stop))))
        deps (calc-deps (dec n) nil #{})]
    (if (contains? m :deps)
      m
      (assoc m :deps deps))))

(fact
 (load-dividers {:path "/12.y1"}) => {:path "/12.y1"
                                      :deps ["/4.y1" "/6.y1"]}
 (let [ws (-> (new-workspace load-dividers identity)
              (add-module {:path "/12.y1"}))]
   (-> ws :mg nodes) => #{"/1.y1" "/2.y1" "/3.y1" "/4.y1" "/6.y1" "/12.y1"}))

```
If the given module already has `:deps`, they are not changed.
```clojure
(fact
 (load-dividers {:path "/12.y1" :deps ["/8.y1"]}) => {:path "/12.y1"
                                                      :deps ["/8.y1"]}
 (let [ws (-> (new-workspace load-dividers identity)
              (add-module {:path "/12.y1"  :deps ["/8.y1"]}))]
   (-> ws :mg nodes) => #{"/1.y1" "/2.y1" "/4.y1" "/8.y1" "/12.y1"}))

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
                   (update ps :modules conj path))
       ws (-> (new-workspace load-dividers eval-func)
              (add-module {:path "/12.y1"})
              (eval-with-deps "/6.y1"))]
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
                          (update ps :before conj path))
       eval-func-now (fn [ps {:keys [path]}]
                       (update ps :now conj path))
       ws (-> (new-workspace load-dividers eval-func-before)
              (add-module {:path "/12.y1"})
              (eval-with-deps "/6.y1")
              (assoc :eval-module eval-func-now)
              (eval-with-deps "/12.y1"))]
   (-> ws :ms (get "/12.y1") :cache :ps) =>
   {:before ["/6.y1" "/3.y1" "/2.y1" "/1.y1"] :now ["/12.y1" "/4.y1"]}))

```
### Invalidation

When a module is updated, its cache, along with the cache of any other module
that had it as a predecessor (whether it was a dependency or not), needs to
be invalidated (deleted).

The function `invalidate-module` takes a workspace and a module ID and
deletes its `:cache` along with that of all its successors.

In the following example we create a workspace and load and evaluate
`/12.y1` and all its dependencies. As before, we use the `:ps` key to
demonstrate the order of evaluation. Then we invalidate `/6.y1`, which is a
successor of both `/12.y1` and `/4.y1` (despite not being a dependency of
`/4.y1`). Then we check that only the other modules are left with a cache.
```clojure
(fact
 (let [eval-func (fn [ps {:keys [path]}]
                   (update ps :modules conj path))
       ws (-> (new-workspace load-dividers eval-func)
              (add-module {:path "/12.y1"})
              (eval-with-deps "/12.y1"))]
   (-> ws :ms (get "/12.y1") :cache :ps) =>
   {:modules ["/12.y1" "/4.y1" "/6.y1" "/3.y1" "/2.y1" "/1.y1"]}
   (set (for [[mid m] (:ms ws)
              :when (contains? m :cache)]
          mid)) => #{"/12.y1" "/4.y1" "/6.y1" "/3.y1" "/2.y1" "/1.y1"}
   (let [ws (-> ws
                (invalidate-module "/6.y1"))]
     (set (for [[mid m] (:ms ws)
                :when (contains? m :cache)]
            mid)) => #{"/3.y1" "/2.y1" "/1.y1"})))

```
## Module Updates

A language server can register to notifications on file updates. When a
notification is received, the server is expected to re-read the updated file
and update its internal state to account for the new contents. This includes
both the state of the module itself as well as the status of all dependent
modules, which may need to be reevaluated.

`update-module` takes a workspace and a module ID (absolute path) as
parameters and returns the updated workspace.

If the module does not pre-exist in the workspace, it is added and evaluated.
```clojure
(fact
 (let [eval-func (fn [ps {:keys [path]}]
                   (update ps :modules conj path))
       ws (-> (new-workspace load-dividers eval-func)
              (update-module "/12.y1"))]
   (-> ws :ms keys set) => #{"/12.y1" "/6.y1" "/4.y1" "/3.y1" "/2.y1" "/1.y1"}
   (-> ws :ms (get "/12.y1") :cache :ps)
   => {:modules ["/12.y1" "/4.y1" "/6.y1" "/3.y1" "/2.y1" "/1.y1"]}))

```
If the module already exists, it is reloaded and reevaluated, but its
dependencies may not be, given that they are already loaded.

We demonstrate this by updating module `/12.y1` twice, first with one set of
load and evaluate functions, and then with another set. The second load
function throws an exception if a module other than `/12.y1` is loaded. The
second evaluate function writes to a different key in the `:ps`.
```clojure
(fact
 (let [eval-func (fn [ps {:keys [path]}]
                   (update ps :old conj path))
       ws (-> (new-workspace load-dividers eval-func)
              (update-module "/12.y1")
              (assoc :load-module #(if (= (:path %) "/12.y1")
                                     {:path "/12.y1"
                                      :deps ["/6.y1" "/4.y1"]
                                      :statements ["This is new"]}
                                     (throw
                                      (Exception.
                                       (str "Trying to load wrong module "
                                            %)))))
              (assoc :eval-module (fn [ps {:keys [path]}]
                                    (when (not= path "/12.y1")
                                      (throw
                                       (Exception.
                                        (str "Trying to eval wrong module "
                                             path))))
                                    (update ps :new conj path)))
              (update-module "/12.y1"))]
   (-> ws :ms (get "/12.y1") :statements) => ["This is new"]
   (-> ws :ms (get "/12.y1") :cache :ps) =>
   {:old ["/4.y1" "/6.y1" "/3.y1" "/2.y1" "/1.y1"]
    :new ["/12.y1"]}))

```
If the updated module has `:refs` (i.e., it was used when evaluating some
other module), the chaches of all modules referring it is invalidated.
```clojure
(fact
 (let [eval-func (fn [ps {:keys [path]}]
                   (update ps :old conj path))
       ws (-> (new-workspace load-dividers eval-func)
              (update-module "/12.y1")
              (update-module "/6.y1"))]
   ;; /4.y1 comes before /6.y1 in the sort order despite not being a dependency.
   (-> ws :ms (get "/4.y1") (contains? :cache)) => false
   (-> ws :ms (get "/3.y1") (contains? :cache)) => true))

```
### Graph Updates

When a module is updated, it is possible that its dependencies change. The
change in depdendencies should be reflected in the graph.

In the following example we update module `/12.y1` twice. First with the
regular `:load-module` function, and then with a function that updates the
dependencies to `/6.y1`, which is already a dependency and `/10.y1`, which is
new. We show that the predecessors of node `/12.y1` in the graph are now
`/6.y1` and `/10.y1`, and that `/5.y1`, a dependency of `/10.y1`, is also
added to the workspace.
```clojure
(fact
 (let [eval-func (fn [ps {:keys [path]}]
                   (update ps :modules conj path))
       ws (-> (new-workspace load-dividers eval-func)
              (update-module "/12.y1")
              (assoc :load-module #(if (= (:path %) "/12.y1")
                                     {:path "/12.y1"
                                      :deps ["/6.y1" "/10.y1"]
                                      :statements ["This is new"]}
                                     (load-dividers %)))
              (update-module "/12.y1"))]
   (-> ws :mg (predecessors "/12.y1")) => #{"/6.y1" "/10.y1"}
   (-> ws :mg (predecessors "/10.y1")) => #{"/2.y1" "/5.y1"}
   (-> ws :ms (contains? "/5.y1")) => true))
```

