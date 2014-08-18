(ns clojure-erlastic.core)
(import '(com.ericsson.otp.erlang OtpErlangAtom OtpErlangObject OtpErlangTuple
                                  OtpErlangString OtpErlangList OtpErlangLong
                                  OtpErlangBinary OtpErlangDouble OtpErlangMap
                                  OtpErlangDecodeException OtpInputStream OtpOutputStream OtpException))
(require '[clojure.core.async :as async :refer [<! >! chan go close!]])

(defn decode [obj]
  (let [t (type obj)]
    (if (= t OtpErlangAtom) (let [k (keyword (.atomValue obj))]
                              (if (#{:true :false} k) (= k :true) k))
      (if (= t OtpErlangList) (seq (map decode (.elements obj)))
        (if (= t OtpErlangTuple) (vec (map decode (.elements obj)))
          (if (= t OtpErlangString) (.stringValue obj)
            (if (= t OtpErlangBinary) (.binaryValue obj)
              (if (= t OtpErlangMap) (zipmap (map decode (.keys obj)) (map decode (.values obj)))
                (if (= t OtpErlangLong) (.longValue obj) 
                  (if (= t OtpErlangDouble) (.doubleValue obj)
                    :decoding_error))))))))))

(defn encode [obj]
  (if (nil? obj) (OtpErlangAtom. "nil")
    (if (seq? obj) (OtpErlangList. (into-array OtpErlangObject (map encode obj)))
      (if (set? obj) (OtpErlangList. (into-array OtpErlangObject (map encode obj)))
        (if (vector? obj) (OtpErlangTuple. (into-array OtpErlangObject (map encode obj)))
          (if (string? obj) (OtpErlangBinary. (bytes (.getBytes obj "UTF-8")))
            (if (keyword? obj) (OtpErlangAtom. (name obj))
              (if (integer? obj) (OtpErlangLong. (long obj))
                (if (float? obj) (OtpErlangDouble. (double obj))
                  (if (map? obj) (OtpErlangMap. (into-array OtpErlangObject (map encode (keys obj))) 
                                                (into-array OtpErlangObject (map encode (vals obj))))
                    (if (= (Class/forName "[B") (type obj)) (OtpErlangBinary. (bytes obj))
                      (if (= java.lang.Boolean (type obj)) (encode (keyword (str obj)))
                        (encode (str obj))))))))))))))

(defn port-connection []
  (let [in (chan) out (chan)]
    (go ;; term receiver coroutine
      (try 
        (while true 
          (let [len-buf (byte-array 4)]
            (.read System/in len-buf)
            (let [term-len (.read4BE (new OtpInputStream len-buf))
                  term-buf (byte-array term-len)]
              (.read System/in term-buf)
              (let [b (decode (.read_any (new OtpInputStream term-buf))) ]
                (>! in b)))))
        (catch Exception e (do 
          (.println System/err (str "receive error : " (type e) " " (.getMessage e))) 
          (close! in) (close! out)
          (System/exit 128)))))
    (go ;; term sender coroutine
      (try
        (while true
          (let [out-term (new OtpOutputStream (encode (<! out)))]
            (doto (new OtpOutputStream) (.write4BE (+ 1 (.size out-term))) (.write 131) (.writeTo System/out))
            (.writeTo out-term System/out)
            (.flush System/out)))
        (catch Exception e (do 
          (.println System/err (str "send error : " (type e) " " (.getMessage e))) 
          (close! in) (close! out)
          (System/exit 128)))))
    [in out] ))
