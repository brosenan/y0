* [Fruit and Vegetables](#fruit-and-vegetables)
```clojure
(ns fruit-veg-example
  (:require [fruit-veg-lang :refer [defruit defvegetable]]))

```
# Fruit and Vegetables

Here we define a few fruit and vegetables using the language we defined
[here](fruit-veg-lang.md).
```clojure
(defruit banana)
(defruit mango)
(defruit grape)

(defvegetable tomato)
(defvegetable cucumber)
(defvegetable cabage)

```
To see how these are imported, go back to
[the statements doc](statements.md#partial-imports).
