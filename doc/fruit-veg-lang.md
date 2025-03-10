* [A Language of Fruit and Vegetables](#a-language-of-fruit-and vegetables)
  * [Exports](#exports)
  * [What Now?](#what-now?)
```clojure
(ns fruit-veg-lang)

```
# A Language of Fruit and Vegetables

The following language allows its users to define symbols as either a fruit
or a vegetable, informing corresponding predicates.
```clojure
(all [f]
     (fruit f ! f "is not a fruit"))

(all [v]
     (vegetable v ! v "is not a vegetable"))

(all [f]
     (defruit f) =>
     (fact
          (fruit f)))

(all [v]
     (defvegetable v) =>
     (fact
          (vegetable v)))

```
## Exports

We would now like to define exports for `defruit` and `defvegetable`. Unlike
[our previous example](example-word-lang.md), here we would like to give the
importer fine-grained control over what they import. Speficially, we would
like to support both symbol-level control (i.e., import individual symbols)
and category-level control (i.e., import all fruit or all vegetables).

To support this, the `export` statement can receive a set of _keys_ before
its header. If this set exists, the export is stored by each of these keys.
```clojure
(all [f]
     (defruit f) =>
     (export #{:fruit f} [f' f]
             (fact
                  (fruit f') <- (fruit f))))

(all [v]
     (defvegetable v) =>
     (export #{:vegetable v} [v' v]
             (fact
                  (vegetable v') <- (vegetable v))))

```
## What Now?

Please continue to the module in which we
[use this language to define fruit and vegetables](fruit-veg-example.md).
