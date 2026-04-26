(ns guarita.wire.in.fraud-score
  (:require [guarita.wire.in.customer :as wire.in.customer]
            [guarita.wire.in.merchant :as wire.in.merchant]
            [guarita.wire.in.terminal :as wire.in.terminal]
            [guarita.wire.in.transaction :as wire.in.transaction]
            [schema.core :as s]))

(s/defschema FraudScore
  {:id               s/Str
   :transaction      wire.in.transaction/Transaction
   :customer         wire.in.customer/Customer
   :merchant         wire.in.merchant/Merchant
   :terminal         wire.in.terminal/Terminal
   (s/optional-key :last-transaction) wire.in.transaction/LastTransaction})
