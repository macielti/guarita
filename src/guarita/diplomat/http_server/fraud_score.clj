(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [guarita.controllers.fraud-score :as controllers.fraud-score]))

(def ^:private ^"[B" approved-true-prefix
  (.getBytes "{\"approved\":true,\"fraud_score\":" "UTF-8"))

(def ^:private ^"[B" approved-false-prefix
  (.getBytes "{\"approved\":false,\"fraud_score\":" "UTF-8"))

(defn- encode-result ^"[B" [{:keys [approved fraud_score]}]
  (let [prefix     (if approved approved-true-prefix approved-false-prefix)
        score      (.getBytes (Double/toString fraud_score) "UTF-8")
        out        (byte-array (+ (alength prefix) (alength score) 1))]
    (System/arraycopy prefix 0 out 0 (alength prefix))
    (System/arraycopy score 0 out (alength prefix) (alength score))
    (aset out (dec (alength out)) (byte (int \})))
    out))

(defn fraud-score!
  [{:keys [json-params components]}]
  (let [body (-> (adapters.fraud-score/wire->fraud-score json-params)
                 (controllers.fraud-score/fraud-score! components)
                 encode-result)]
    {:status  200
     :headers {"Content-Type"   "application/json"
               "Content-Length" (str (alength body))}
     :body    body}))
