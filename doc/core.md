```clojure
(ns y0.core-test 
  (:require [midje.sweet :refer [fact =>]]
            [y0.core :refer :all]
            [edamame.core :as e :refer [parse-string]]))

(fact
 (+ 1 2) => 3)
```

