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
