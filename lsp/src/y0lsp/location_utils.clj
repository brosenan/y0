(ns y0lsp.location-utils 
  (:require
   [clojure.java.io :as io]
   [y0.location-util :refer [decode-file-pos encode-file-pos]]))

(defn from-lsp-pos [{:keys [line character]}]
  (encode-file-pos (inc line) (inc character)))

(defn to-lsp-pos [pos]
  (let [[line character] (decode-file-pos pos)]
    {:line (dec line) :character (dec character)}))

(defn- uri-to-path [uri]
  (-> uri io/as-url io/file .getAbsolutePath))

(defn- text-doc-to-path [{:keys [uri]}]
  (uri-to-path uri))

(defn from-lsp-text-doc-pos [{:keys [text-document position]}]
  [(text-doc-to-path text-document) (from-lsp-pos position)])