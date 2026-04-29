(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [guarita.controllers.fraud-score :as controllers.fraud-score]
            [schema.core :as s]))

(s/defn fraud-score!
  [{{:keys [fraud-score]} :json-params}]
  (let [result (-> (adapters.fraud-score/wire->fraud-score fraud-score)
                   controllers.fraud-score/fraud-score!)]
    {:status 200
     :body   result}))
