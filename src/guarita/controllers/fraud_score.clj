(ns guarita.controllers.fraud-score
  (:require [guarita.models.fraud-score :as models.fraud-score]
            [schema.core :as s]))

(s/defn fraud-score! :- models.fraud-score/FraudScoreResult
  [_fraud-score :- models.fraud-score/FraudScore]
  {:approved    false
   :fraud-score 1.0})
