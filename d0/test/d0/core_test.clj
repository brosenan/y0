(ns d0.core-test
  (:require [midje.sweet :refer [fact =>]]
            [d0.core]))

(fact "placeholder"
      (+ 1 1) => 2)
