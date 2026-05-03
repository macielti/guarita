(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.controllers.fraud-score :as controllers.fraud-score]))

(def ^:private threshold 0.6)

(defn fraud-score!
  [{:keys [json-params components]}]
  (let [score   (controllers.fraud-score/fraud-score! json-params components)
        approved (< score threshold)
        body    ^"[B" (.getBytes (str "{\"approved\":" approved ",\"fraud_score\":" score "}") "UTF-8")]
    {:status  200
     :headers {"Content-Type"   "application/json"
               "Content-Length" (str (alength body))}
     :body    body}))
