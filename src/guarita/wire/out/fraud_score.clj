(ns guarita.wire.out.fraud-score
  (:require [schema.core :as s]))

(s/defschema FraudScore
  {:approved    s/Bool
   :fraud_score s/Num})
