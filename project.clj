(defproject y0 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [borkdude/edamame "1.4.25"]
                 [org.clojure/tools.cli "1.1.230"]
                 [clojure-term-colors "0.1.0"]
                 [instaparse "1.5.0"]
                 [aysylu/loom "1.0.2"]
                 [hiccup "2.0.0-RC3"]]
  :profiles {:dev {:dependencies [[midje "1.10.10"]]}}
  :plugins [[lein-midje "3.2.2"]]
  :repl-options {:init-ns y0.core})
