(ns clojure-erlastic.core-test
  (:require clojure-erlastic.core [clojure.test :refer :all]))

(deftest encoding-test
  (let [conf {:str-detect :none :convention :elixir :str-autodetect-len 10}
        encode (fn [obj] (clojure-erlastic.core/encode obj conf))
        decode (fn [obj] (clojure-erlastic.core/decode obj conf))]
  (testing "Keyword encoding"
    (is (= (-> :toto encode decode) :toto)))
  (testing "Map encoding"
    (is (= (-> {:a :b :c :d} encode decode) {:a :b :c :d})))
  (testing "Vector encoding"
    (is (= (-> [:a :b :c :d] encode decode) [:a :b :c :d])))
  (testing "List encoding"
    (is (= (-> '(:a :b :c :d) encode decode) '(:a :b :c :d))))
  (testing "Set encoding : not bijective, set is list"
    (is (= (-> #{:a :b :c :d} encode decode set) #{:a :b :c :d})))
  (testing "Float encoding"
    (is (= (-> 4.3 encode decode) 4.3)))
  (testing "Bool encoding"
    (is (= (-> true encode decode) true)))
  (testing "Binary encoding"
    (is (= (-> (byte-array (repeat 4 0)) encode decode seq) (seq (byte-array (repeat 4 0))))))
  (testing "Nil encoding"
    (is (= (-> nil encode decode) nil)))
  (testing "Char encoding"
    (is (= (-> \c encode decode char) \c)))
  (testing "Elixir convention : string is binary"
    (is (= (-> "toto" encode decode String.) "toto")))
  (testing "Elixir convention : string codec not reflexive"
    (is (not= (-> "toto" encode decode) "toto")))
  (testing "Elixir convention : nil is :nil"
    (is (= (-> nil encode .atomValue) "nil")))))

(deftest elixir-str-detect
  (let [conf {:str-detect :auto :convention :elixir :str-autodetect-len 10}
        encode (fn [obj] (clojure-erlastic.core/encode obj conf))
        decode (fn [obj] (clojure-erlastic.core/decode obj conf))]
  (testing "Elixir : string is binary and reflexive codec"
    (is (= (-> "€é;-[" encode decode) "€é;-[")))
  (testing "Elixir : works if char split by detect len"
    (is (= (-> "€€€€" encode decode) "€€€€")))
  (testing "Elixir: no string when broken utf8"
    (is (not= (type (decode (byte-array '(116 111 226 130)))) java.lang.String)))))

(deftest erlang-str-detect
  (let [conf {:str-detect :auto :convention :erlang :str-autodetect-len 10}
        encode (fn [obj] (clojure-erlastic.core/encode obj conf))
        decode (fn [obj] (clojure-erlastic.core/decode obj conf))]
  (testing "erlang : string encode to list"
    (is (= (-> "€é;-[" encode (.elements) first .longValue) 8364)))
  (testing "erlang : string reflexive codec"
    (is (= (-> "€é;-[" encode decode) "€é;-[")))
  (testing "erlang : works if char split by detect len"
    (is (= (-> "€€€€" encode decode) "€€€€")))
  (testing "Elixir: no string when broken utf8"
    (is (not= (type (decode (byte-array '(116 111 226 130)))) java.lang.String)))))
