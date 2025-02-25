(ns y0lsp.location-utils 
  (:require
   [clojure.java.io :as io]
   [y0.location-util :refer [decode-file-pos encode-file-pos]]
   [y0lsp.tree-index :refer [find-node]]))

(defn from-lsp-pos [{:keys [line character]}]
  (encode-file-pos (inc line) (inc character)))

(defn to-lsp-pos [pos]
  (let [[line character] (decode-file-pos pos)]
    {:line (dec line) :character (dec character)}))

(defn uri-to-path [uri]
  (-> uri io/as-url io/file .getAbsolutePath))

(defn- path-to-uri [path]
  (str "file://" (-> path io/file .getAbsolutePath)))

(defn- text-doc-to-path [{:keys [uri]}]
  (uri-to-path uri))

(defn from-lsp-text-doc-pos [{:keys [text-document position]}]
  [(text-doc-to-path text-document) (from-lsp-pos position)])

(defn to-lsp-location [{:keys [start end path]}]
  {:uri (path-to-uri path)
   :range {:start (to-lsp-pos start)
           :end (to-lsp-pos end)}})

(defn node-at-text-doc-pos [{:keys [ws]} {:keys [text-document position]}]
  (let [path (-> text-document :uri uri-to-path)
        index (-> @ws :ms (get path) :index)
        pos (from-lsp-pos position)]
    (find-node index pos)))

(defn node-location [node]
  (-> node meta to-lsp-location))