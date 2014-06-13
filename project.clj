(defproject clojure-erlastic "0.1.0"
  :description "Micro lib making use of erlang JInterface lib to decode and
                encode Binary Erlang Term and simple erlang port interface with
                core.async channel. So you can communicate with erlang coroutine
                with clojure abstraction"
  :url "https://github.com/awetzel/clojure-erlastic"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [erlang/jinterface "1.5.9"]]
  :repositories {"local" ~(str (.toURI (java.io.File. "local-repo")))})
