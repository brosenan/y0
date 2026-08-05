(ns d0.compiler)

(defn compile [tree src-ps d0-ps]
  {:ok [`(defn ~(symbol "main") [~(symbol "args")]
           42)]})
