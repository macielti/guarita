(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [guarita.controllers.fraud-score :as controllers.fraud-score]
            [schema.core :as s]))

(s/defn fraud-score!
  [{:keys [json-params components]}]
  (let [fraud-score (:fraud-score json-params)
        result (-> (adapters.fraud-score/wire->fraud-score fraud-score)
                   (controllers.fraud-score/fraud-score! components))]
    {:status 200
     :body   result}))
