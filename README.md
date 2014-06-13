clojure-erlastic
================

Micro lib making use of erlang JInterface lib to decode and encode Binary
Erlang Term and simple erlang port interface with core.async channel. So you
can communicate with erlang coroutine with clojure abstraction

Last version of JInterface (from erlang 17.0) is embedded inside a maven repo.

## Usage

`port-connection` creates two channels that you can use to
communicate respectively in and out with the calling erlang port.
The objects you put or receive throught these channels are encoded
and decoded into erlang binary term following these rules :

- erlang atom is clojure keyword
- erlang list is clojure list
- erlang tuple is clojure vector
- erlang binary is clojure bytes[]
- erlang integer is clojure long
- erlang float is clojure double
- erlang map is clojure map (thanks to erlang 17.0)
- clojure string is erlang binary

For instance, here is a simple echo server :

```clojure
(let [[in out] (clojure-erlastic.core/port-connection)]
  (<!! (go (while true
    (>! out (<! in))))))
```

More advanced : a clojure server managing an integer, you can add or
delete an integer, and get the current number

```clojure
(let [[in out] (clojure-erlastic.core/port-connection)]
  (<!! (go 
    (loop [num 0]
      (match (<! in)
        [:add n] (recur (+ num n))
        [:rem n] (recur (- num n))
        :get (do (>! out num) (recur num)))))))
```

Usage in elixir 

```elixir
defmodule CljPort do
  def start, do: 
    Port.open({:spawn,'java -cp xxx.jar clojure.main numserver.clj'},[:binary, packet: 4])
  def psend(port,data), do: 
    send(port,{self,{:command,:erlang.term_to_binary(data)}})
  def preceive(port), do: 
    receive(do: ({^port,{:data,b}}->:erlang.binary_to_term(b)))
end

port = CljPort.start

CljPort.psend(port, {:add,3})
CljPort.psend(port, {:rem,2})
CljPort.psend(port, {:add,5})
6 = CljPort.preceive(port, :get)
```

