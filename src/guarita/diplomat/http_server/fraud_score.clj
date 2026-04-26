(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [schema.core :as s]))

(s/defn fraud-score!
  [{{:keys [fraud-score]} :json-params}]
  (let [_model (adapters.fraud-score/wire->fraud-score fraud-score)]
    {:status 200
     :body   {:approved    false
              :fraud_score 1.0}}))
