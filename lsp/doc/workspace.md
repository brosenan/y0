```clojure
(ns y0lsp.workspace-test
  (:require [midje.sweet :refer [fact =>]]
            [y0lsp.workspace :refer :all]
            [loom.graph :refer [graph? directed?]]))

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

