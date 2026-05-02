(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [jsonista.core :as j]
            [guarita.controllers.fraud-score :as controllers.fraud-score]
            [schema.core :as s]))

(s/defn fraud-score!
  [{:keys [json-params components]}]
  {:status 200
   :body   ^bytes (-> (adapters.fraud-score/wire->fraud-score json-params)
                      (controllers.fraud-score/fraud-score! components)
                      (j/write-value-as-bytes j/keyword-keys-object-mapper))})
