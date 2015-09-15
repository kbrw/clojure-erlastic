(ns clojure-erlastic.core)
(import '(com.ericsson.otp.erlang OtpErlangAtom OtpErlangObject OtpErlangTuple
                                  OtpErlangString OtpErlangList OtpErlangLong
                                  OtpErlangBinary OtpErlangDouble OtpErlangMap
                                  OtpErlangDecodeException OtpInputStream OtpOutputStream OtpException)
        '(java.nio.charset Charset) '(java.nio ByteBuffer CharBuffer))
(require '[clojure.core.async :as async :refer [<! >! <!! chan go close!]])

;; CONFIGURATION
(def default-config {
  :str-detect :none
  :convention :elixir
  :str-autodetect-len 10 })
(defn get-conf [config key] 
  (if (contains? config key) (config key) (default-config key)))

;; String conversion and detection utility functions
(def utf-8 "The UTF-8 charset object" (Charset/forName "UTF-8"))
(defn printable? [c]
  (#{Character/UPPERCASE_LETTER Character/TITLECASE_LETTER Character/SPACE_SEPARATOR Character/PARAGRAPH_SEPARATOR Character/LOWERCASE_LETTER 
     Character/CURRENCY_SYMBOL Character/DECIMAL_DIGIT_NUMBER
     Character/DASH_PUNCTUATION Character/CONNECTOR_PUNCTUATION Character/END_PUNCTUATION Character/FINAL_QUOTE_PUNCTUATION Character/INITIAL_QUOTE_PUNCTUATION Character/START_PUNCTUATION Character/OTHER_PUNCTUATION} 
      (Character/getType c)))

(defn bin-str? [bin detect-len]
  (let [char-res (CharBuffer/allocate detect-len)
        len (min (alength bin) detect-len)
        coder-res (.decode (.newDecoder utf-8) (ByteBuffer/wrap bin 0 len) char-res (= len (alength bin)))
        char-len (.position char-res)]
    (doto char-res (.rewind) (.limit char-len))
    (and (not (.isError coder-res)) (every? printable? (.toString char-res)))))

(defn seq-str? [lst detect-len]
  (every? #(and (Character/isValidCodePoint %) (printable? %)) (take detect-len lst)))

(defn str-or-bin [bin]
  (try (String. bin utf-8)
       (catch Exception e bin)))

(defn str-or-lst [lst]
  (try (apply str (map char lst)) 
       (catch Exception e lst)))
        
;; Codec functions
(defn decode 
  ([obj] (decode obj default-config))
  ([obj config]
    (let [t (type obj)
          conf (fn [key] (get-conf config key))
          dec (fn [obj] (decode obj config))]
      (cond 
        (= t OtpErlangAtom) (let [k (keyword (.atomValue obj))]
          (cond
            (#{:true :false} k) (= k :true) 
            (and (= (conf :convention) :elixir) (= k :nil)) nil
            (and (= (conf :convention) :erlang) (= k :undefined)) nil
            :else k))
        (= t OtpErlangList) (let [elem-seq (seq (map dec (.elements obj)))]
          (cond 
            (and (= (conf :convention) :erlang) (= (conf :str-detect) :all))
              (str-or-lst elem-seq)
            (and (= (conf :convention) :erlang) (= (conf :str-detect) :auto))
              (if (seq-str? elem-seq (conf :str-autodetect-len)) (str-or-lst elem-seq) elem-seq)
            :else elem-seq))
        (= t OtpErlangTuple) (vec (map dec (.elements obj)))
        (= t OtpErlangString) (.stringValue obj)
        (= t OtpErlangBinary) (let [bin (.binaryValue obj)]
          (cond 
            (and (= (conf :convention) :elixir) (= (conf :str-detect) :all)) 
              (str-or-bin bin)
            (and (= (conf :convention) :elixir) (= (conf :str-detect) :auto))
              (if (bin-str? bin (conf :str-autodetect-len)) (str-or-bin bin) bin)
            :else bin))
        (= t OtpErlangMap) (zipmap (map dec (.keys obj)) (map dec (.values obj)))
        (= t OtpErlangLong) (.longValue obj)
        (= t OtpErlangDouble) (.doubleValue obj)
        :else   :decoding_error))))

(defn encode
  ([obj] (encode obj default-config))
  ([obj config]
   (letfn [(conf [key] (get-conf config key))
           (enc [obj] (encode obj config))]
     (cond
       (nil? obj)
         (if (= (conf :convention) :elixir)
             (OtpErlangAtom. "nil")
             (OtpErlangAtom. "undefined"))
       (char? obj) (enc (int obj))
       (seq? obj) (OtpErlangList. (into-array OtpErlangObject (map enc obj)))
       (set? obj) (OtpErlangList. (into-array OtpErlangObject (map enc obj)))
       (vector? obj) (OtpErlangTuple. (into-array OtpErlangObject (map enc obj)))
       (string? obj) 
         (if (= (conf :convention) :elixir) 
             (OtpErlangBinary. (bytes (.getBytes obj "UTF-8")))
             (enc (seq obj)))
       (keyword? obj) (OtpErlangAtom. (name obj))
       (integer? obj) (OtpErlangLong. (long obj))
       (float? obj) (OtpErlangDouble. (double obj))
       (map? obj) (OtpErlangMap. (into-array OtpErlangObject (map enc (keys obj))) 
                                 (into-array OtpErlangObject (map enc (vals obj))))
       (= (Class/forName "[B") (type obj)) (OtpErlangBinary. (bytes obj))
       (= java.lang.Boolean (type obj)) (enc (keyword (str obj)))
       :else   (enc (str obj))))))

;; Erlang port communication on stderr - stdin
(defn log [& params]
  (.println System/err (apply str params)))

(defn port-connection 
  ([] (port-connection default-config))
  ([config]
    (let [in (chan) out (chan)]
      (go ;; term receiver coroutine
        (try 
          (while true 
            (let [len-buf (byte-array 4)]
              (.read System/in len-buf)
              (let [term-len (.read4BE (new OtpInputStream len-buf))
                    term-buf (byte-array term-len)]
                (.read System/in term-buf)
                (let [b (decode (.read_any (new OtpInputStream term-buf)) config) ]
                  (>! in b)))))
          (catch Exception e (do 
            (log "receive error : " (type e) " " (.getMessage e)) 
            (close! out) (close! in)))))
      (go ;; term sender coroutine
        (loop []
          (when-let [term (<! out)]
            (try
              (let [out-term (new OtpOutputStream (encode term config))]
                (doto (new OtpOutputStream) (.write4BE (+ 1 (.size out-term))) (.write 131) (.writeTo System/out))
                (.writeTo out-term System/out)
                (.flush System/out))
              (catch Exception e (log "send error : " (type e) " " (.getMessage e))))
            (recur))))
      [in out] )))

;; erlang style genserver management
(defn run-server 
  ([handle] (run-server (fn [state] state) handle default-config))
  ([init handle] (run-server init handle default-config))
  ([init handle config]
    (let [[in out] (port-connection config)]
      (<!! (go
        (loop [state (init (<! in))]
          (if-let [req (<! in)]
            (let [res (try (handle req state) (catch Exception e [:stop [:error e]]))]
              (case (res 0)
                :reply (do (>! out (res 1)) (recur (res 2)))
                :noreply (recur (res 1))
                :stop (res 1)))
            :normal)))))))
