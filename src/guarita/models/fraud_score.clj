(ns guarita.models.fraud-score
  (:require [guarita.models.customer :as models.customer]
            [guarita.models.merchant :as models.merchant]
            [guarita.models.terminal :as models.terminal]
            [guarita.models.transaction :as models.transaction]
            [schema.core :as s]))

(s/defschema FraudScore
  {:id               s/Str
   :transaction      models.transaction/Transaction
   :customer         models.customer/Customer
   :merchant         models.merchant/Merchant
   :terminal         models.terminal/Terminal
   (s/optional-key :last-transaction) models.transaction/LastTransaction})

(s/defschema FraudScoreVector
  [(s/one s/Num "amount")
   (s/one s/Num "installments")
   (s/one s/Num "amount-vs-avg")
   (s/one s/Num "hour-of-day")
   (s/one s/Num "day-of-week")
   (s/one s/Num "minutes-since-last-tx")
   (s/one s/Num "km-from-last-tx")
   (s/one s/Num "km-from-home")
   (s/one s/Num "tx-count-24h")
   (s/one s/Num "is-online")
   (s/one s/Num "card-present")
   (s/one s/Num "unknown-merchant")
   (s/one s/Num "mcc-risk")
   (s/one s/Num "merchant-avg-amount")])

(s/defschema FraudScoreResult
  {:approved    s/Bool
   :fraud_score s/Num})
