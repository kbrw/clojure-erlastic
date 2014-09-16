(ns clojure-erlastic.core)
(import '(com.ericsson.otp.erlang OtpErlangAtom OtpErlangObject OtpErlangTuple
                                  OtpErlangString OtpErlangList OtpErlangLong
                                  OtpErlangBinary OtpErlangDouble OtpErlangMap
                                  OtpErlangDecodeException OtpInputStream OtpOutputStream OtpException))
(require '[clojure.core.async :as async :refer [<! >! <!! chan go close!]])

(defn decode [obj]
  (let [t (type obj)]
    (cond 
      (= t OtpErlangAtom) (let [k (keyword (.atomValue obj))]
                              (if (#{:true :false} k) (= k :true) k))
      (= t OtpErlangList) (seq (map decode (.elements obj)))
      (= t OtpErlangTuple) (vec (map decode (.elements obj)))
      (= t OtpErlangString) (.stringValue obj)
      (= t OtpErlangBinary) (.binaryValue obj)
      (= t OtpErlangMap) (zipmap (map decode (.keys obj)) (map decode (.values obj)))
      (= t OtpErlangLong) (.longValue obj)
      (= t OtpErlangDouble) (.doubleValue obj)
      :else   :decoding_error)))

(defn encode [obj]
  (cond
    (nil? obj) (OtpErlangAtom. "nil")
    (seq? obj) (OtpErlangList. (into-array OtpErlangObject (map encode obj)))
    (set? obj) (OtpErlangList. (into-array OtpErlangObject (map encode obj)))
    (vector? obj) (OtpErlangTuple. (into-array OtpErlangObject (map encode obj)))
    (string? obj) (OtpErlangBinary. (bytes (.getBytes obj "UTF-8")))
    (keyword? obj) (OtpErlangAtom. (name obj))
    (integer? obj) (OtpErlangLong. (long obj))
    (float? obj) (OtpErlangDouble. (double obj))
    (map? obj) (OtpErlangMap. (into-array OtpErlangObject (map encode (keys obj))) 
                              (into-array OtpErlangObject (map encode (vals obj))))
    (= (Class/forName "[B") (type obj)) (OtpErlangBinary. (bytes obj))
    (= java.lang.Boolean (type obj)) (encode (keyword (str obj)))
    :else   (encode (str obj))))

(defn log [& params]
  (.println System/err (apply str params)))

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
          (log "receive error : " (type e) " " (.getMessage e)) 
          (close! out) (close! in)))))
    (go ;; term sender coroutine
      (loop []
        (when-let [term (<! out)]
          (try
            (let [out-term (new OtpOutputStream (encode term))]
              (doto (new OtpOutputStream) (.write4BE (+ 1 (.size out-term))) (.write 131) (.writeTo System/out))
              (.writeTo out-term System/out)
              (.flush System/out))
            (catch Exception e (log "send error : " (type e) " " (.getMessage e))))
          (recur))))
    [in out] ))

(defn run-server 
  ([handle] (run-server (fn [state] state) handle))
  ([init handle]
    (let [[in out] (port-connection)]
      (<!! (go
        (loop [state (init (<! in))]
          (if-let [req (<! in)]
            (let [res (try (handle req state) (catch Exception e [:stop [:error e]]))]
              (case (res 0)
                :reply (do (>! out (res 1)) (recur (res 2)))
                :noreply (recur (res 1))
                :stop (res 1)))
            :normal)))))))
