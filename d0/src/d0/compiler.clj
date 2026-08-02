(ns d0.compiler)

(defn compile [src-ps d0-ps]
  {:ok [`(defn ~(symbol "main") [~(symbol "args")]
           42)]})
