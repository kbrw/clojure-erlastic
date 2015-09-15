# Changelog

## v0.3.0

* Enhancements
  * add `config` option to every functions
  * handle config for elixir or erlang conventions (string and nil)
  * 3 modes available for string detection : respectively to erlang/elixir,
    decode all list/binary as string, never decode as string, or autodetect 
    with a test on a configurable number of first elem/bytes.

