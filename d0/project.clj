(defproject d0 "0.0.1-SNAPSHOT"
  :description "A language for defining dynamic semantics"
  :url "https://github.com/brosenan/y0/tree/main/d0"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [borkdude/edamame "1.4.25"]
                 [org.clojure/tools.cli "1.1.230"]
                 [clojure-term-colors "0.1.0"]
                 [instaparse "1.5.0"]
                 [aysylu/loom "1.0.2"]
                 [hiccup "2.0.0-RC3"]]
  :profiles {:dev {:dependencies [[midje "1.10.10"]]
                   :resource-paths ["../" "../y0_test/"]}
             :uberjar {:aot :all}}
  :plugins [[lein-midje "3.2.2"]]
  :source-paths ["src" "../src"]
  :clean-targets ^{:protect false} [:target-path])
