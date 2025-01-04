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
   (nodes (:mg ws)) => #{"y1:foo"}))
```

