(ns y0lsp.workspace
  (:require [loom.graph :refer [digraph]]
            [loom.graph :as graph]))

(defn new-workspace []
  {:ms {}
   :mg (digraph)})