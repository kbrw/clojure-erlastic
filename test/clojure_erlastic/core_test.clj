(ns clojure-erlastic.core-test
  (:require [clojure.test :refer :all]
            [clojure-erlastic.core :refer :all]))

(deftest encoding-test
  (testing "Keyword encoding"
    (is (= (-> :toto encode decode) :toto)))
  (testing "Map encoding"
    (is (= (-> {:a :b :c :d} encode decode) {:a :b :c :d})))
  (testing "Vector encoding"
    (is (= (-> [:a :b :c :d] encode decode) [:a :b :c :d])))
  (testing "List encoding"
    (is (= (-> '(:a :b :c :d) encode decode) '(:a :b :c :d))))
  (testing "Float encoding"
    (is (= (-> 4.3 encode decode) 4.3)))
  (testing "Binary encoding"
    (is (= (-> (byte-array (repeat 4 0)) encode decode seq) (seq (byte-array (repeat 4 0))))))
  (testing "String encoding not reflective : string is binary"
    (is (= (-> "toto" encode decode String.) "toto"))))
