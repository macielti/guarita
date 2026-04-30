(ns guarita.adapters.fraud-score
  (:require [guarita.adapters.customer :as adapters.customer]
            [guarita.adapters.merchant :as adapters.merchant]
            [guarita.adapters.terminal :as adapters.terminal]
            [guarita.adapters.transaction :as adapters.transaction]
            [guarita.models.fraud-score :as models.fraud-score]
            [guarita.wire.in.fraud-score :as wire.in.fraud-score]
            [medley.core :as medley]
            [schema.core :as s]))

(s/defn wire->fraud-score :- models.fraud-score/FraudScore
  [{:keys [id transaction customer merchant terminal last_transaction]}
   :- wire.in.fraud-score/FraudScore]
  (medley/assoc-some {:id          id
                      :transaction (adapters.transaction/wire->transaction transaction)
                      :customer    (adapters.customer/wire->customer customer)
                      :merchant    (adapters.merchant/wire->merchant merchant)
                      :terminal    (adapters.terminal/wire->terminal terminal)}
                     :last-transaction (some-> last_transaction
                                               adapters.transaction/wire->last-transaction)))
