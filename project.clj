(defproject clojure-erlastic "0.1.2"
  :description "Micro lib making use of erlang JInterface lib to decode and
                encode Binary Erlang Term and simple erlang port interface with
                core.async channel. So you can communicate with erlang coroutine
                with clojure abstraction"
  :url "https://github.com/awetzel/clojure-erlastic"
  :scm {:name "git" :url "https://github.com/awetzel/clojure-erlastic"}
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.erlang.otp/jinterface "1.5.9"]]
  :repositories {"scalaris" "https://scalaris.googlecode.com/svn/maven/"})
