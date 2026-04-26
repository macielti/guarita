(ns fixtures.fraud-score
  (:require [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.wire.in.customer :as wire.in.customer]
            [guarita.wire.in.fraud-score :as wire.in.fraud-score]
            [guarita.wire.in.merchant :as wire.in.merchant]
            [guarita.wire.in.terminal :as wire.in.terminal]
            [guarita.wire.in.transaction :as wire.in.transaction]))

(def wire-in-transaction
  (helpers.schema/generate wire.in.transaction/Transaction
                           {:requested-at "2024-06-15T10:30:00Z"}
                           {}))

(def wire-in-last-transaction
  (helpers.schema/generate wire.in.transaction/LastTransaction
                           {:timestamp "2024-06-14T08:00:00Z"}
                           {}))

(def wire-in-customer
  (helpers.schema/generate wire.in.customer/Customer {} {}))

(def wire-in-merchant
  (helpers.schema/generate wire.in.merchant/Merchant {} {}))

(def wire-in-terminal
  (helpers.schema/generate wire.in.terminal/Terminal {} {}))

(def wire-in-fraud-score
  (helpers.schema/generate wire.in.fraud-score/FraudScore
                           {:id               "tx-1329056812"
                            :transaction      wire-in-transaction
                            :customer         wire-in-customer
                            :merchant         wire-in-merchant
                            :terminal         wire-in-terminal
                            :last-transaction wire-in-last-transaction}
                           {}))

(def wire-in-fraud-score-no-last-transaction
  (dissoc wire-in-fraud-score :last-transaction))
