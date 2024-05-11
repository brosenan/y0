```clojure
(ns y0.core-test 
  (:require [midje.sweet :refer [fact =>]]
            [y0.core :refer :all]))

(fact
 (+ 1 2) => 3)
```

