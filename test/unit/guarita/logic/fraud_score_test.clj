(ns guarita.logic.fraud-score-test
  (:require [clojure.test :refer [is testing]]
            [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.logic.fraud-score :as logic.fraud-score]
            [guarita.models.customer :as models.customer]
            [guarita.models.fraud-score :as models.fraud-score]
            [guarita.models.merchant :as models.merchant]
            [guarita.models.normalization :as models.normalization]
            [guarita.models.terminal :as models.terminal]
            [guarita.models.transaction :as models.transaction]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s])
  (:import [java.time Instant]))

;; normalize-mcc-risk test fixtures
(def mcc-risk {"5411" 0.15
               "7995" 0.85
               "5999" 0.50})

(def mcc-known-low "5411")
(def expected-mcc-known-low-score 0.15)

(def mcc-known-high "7995")
(def expected-mcc-known-high-score 0.85)

(def mcc-unknown "9999")
(def expected-mcc-unknown-score 0.5)

;; Shared fixtures
(def max-amount 1000)
(def max-installments 12)
(def amount-vs-avg-ratio 2)
(def avg-amount 1000.0)

(def normalization
  (helpers.schema/generate models.normalization/Normalization
                           {:max-amount              max-amount
                            :max-installments        max-installments
                            :amount-vs-avg-ratio     amount-vs-avg-ratio}
                           {}))

(def customer
  (helpers.schema/generate models.customer/Customer
                           {:avg-amount avg-amount}
                           {}))

;; normalize-hour-of-day test fixtures
(def hour-midnight 0)
(def expected-hour-midnight-score 0.0)

(def hour-noon 12)
(def expected-hour-noon-score (/ 12 23.0))

(def hour-end-of-day 23)
(def expected-hour-end-of-day-score 1.0)

(def hour-morning 6)
(def expected-hour-morning-score (/ 6 23.0))

;; normalize-day-of-week test fixtures
(def day-monday 0)
(def expected-day-monday-score 0.0)

(def day-saturday 5)
(def expected-day-saturday-score (/ 5 6.0))

(def day-sunday 6)
(def expected-day-sunday-score 1.0)

(def day-wednesday 2)
(def expected-day-wednesday-score (/ 2 6.0))

;; normalize-minutes-since-last-tx test fixtures
(def max-minutes 60)
(def minutes-elapsed-30 30)
(def expected-minutes-30-score 0.5)

(def minutes-elapsed-120 120)
(def expected-minutes-120-score 1.0)

(def minutes-elapsed-negative -5)
(def expected-minutes-negative-score 0.0)

;; normalize-km-from-last-tx test fixtures
(def max-km 100)
(def km-from-current-50 50)
(def expected-km-50-score 0.5)

(def km-from-current-200 200)
(def expected-km-200-score 1.0)

(def km-from-current-0 0)
(def expected-km-0-score 0.0)

;; normalize-km-from-home test fixtures
(def km-from-home-0 0)
(def expected-km-home-0-score 0.0)

(def km-from-home-50 50)
(def expected-km-home-50-score 0.5)

(def km-from-home-100 100)
(def expected-km-home-100-score 1.0)

(def km-from-home-200 200)
(def expected-km-home-200-score 1.0)

;; normalize-tx-count-24h test fixtures
(def max-tx-count-24h 10)
(def tx-count-0 0)
(def expected-tx-count-0-score 0.0)

(def tx-count-5 5)
(def expected-tx-count-5-score 0.5)

(def tx-count-10 10)
(def expected-tx-count-10-score 1.0)

(def tx-count-20 20)
(def expected-tx-count-20-score 1.0)

;; normalize-is-online test fixtures
(def terminal-online (helpers.schema/generate models.terminal/Terminal
                                              {:online? true}
                                              {}))

(def terminal-offline (helpers.schema/generate models.terminal/Terminal
                                               {:online? false}
                                               {}))

(def expected-online-score 1)
(def expected-offline-score 0)

;; normalize-card-present test fixtures
(def terminal-card-present (helpers.schema/generate models.terminal/Terminal
                                                    {:card-present? true}
                                                    {}))

(def terminal-card-not-present (helpers.schema/generate models.terminal/Terminal
                                                        {:card-present? false}
                                                        {}))

(def expected-card-present-score 1)
(def expected-card-not-present-score 0)

;; normalize-unknown-merchant test fixtures
(def merchant-known "merch-1")
(def merchant-unknown "merch-9")
(def known-merchants-input ["merch-1" "merch-2"])
(def empty-known-merchants-input [])
(def known-merchants #{"merch-1" "merch-2"})
(def empty-known-merchants #{})

(def customer-with-known-merchants
  (helpers.schema/generate models.customer/Customer
                           {:known-merchants known-merchants}
                           {}))

(def customer-with-empty-known-merchants
  (helpers.schema/generate models.customer/Customer
                           {:known-merchants empty-known-merchants}
                           {}))

(def expected-known-merchant-score 0)
(def expected-unknown-merchant-score 1)

;; normalize-merchant-avg-amount test fixtures
(def max-merchant-avg-amount 1000)
(def merchant-avg-amount-0 0)
(def expected-merchant-avg-0-score 0.0)

(def merchant-avg-amount-500 500)
(def expected-merchant-avg-500-score 0.5)

(def merchant-avg-amount-1000 1000)
(def expected-merchant-avg-1000-score 1.0)

(def merchant-avg-amount-2000 2000)
(def expected-merchant-avg-2000-score 1.0)

;; normalize-amount test fixtures
(def normal-amount 500.0)
(def expected-normal-amount-score 0.5)

(def above-max-amount 2000.0)
(def expected-clamped-high-amount-score 1.0)

(def negative-amount -100.0)
(def expected-clamped-low-amount-score 0.0)

(def boundary-amount 1000.0)
(def expected-boundary-amount-score 1.0)

;; normalize-installments test fixtures
(def normal-installments 6)
(def expected-normal-installments-score 0.5)

(def above-max-installments 24)
(def expected-clamped-high-installments-score 1.0)

(def negative-installments -1)
(def expected-clamped-low-installments-score 0.0)

(def boundary-installments 12)
(def expected-boundary-installments-score 1.0)

;; normalize-amount-vs-avg test fixtures
(def amount-for-normal-avg-ratio 500.0)
(def expected-normal-avg-ratio-score 0.25)

(def amount-for-high-avg-ratio 5000.0)
(def expected-clamped-high-avg-ratio-score 1.0)

(def amount-for-low-avg-ratio -100.0)
(def expected-clamped-low-avg-ratio-score 0.0)

(def amount-for-boundary-avg-ratio 2000.0)
(def expected-boundary-avg-ratio-score 1.0)

;; normalize-amount tests
(s/deftest normalize-amount-normal-test
  (testing "it should return a proportional score when amount is less than max-amount"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       normal-amount
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount transaction normalization)]
      (is (match? expected-normal-amount-score result)))))

(s/deftest normalize-amount-clamped-high-test
  (testing "it should clamp score to 1.0 when amount exceeds max-amount"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       above-max-amount
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount transaction normalization)]
      (is (match? expected-clamped-high-amount-score result)))))

(s/deftest normalize-amount-clamped-low-test
  (testing "it should clamp score to 0.0 when amount is negative"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       negative-amount
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount transaction normalization)]
      (is (match? expected-clamped-low-amount-score result)))))

(s/deftest normalize-amount-boundary-test
  (testing "it should return exactly 1.0 when amount equals max-amount"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       boundary-amount
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount transaction normalization)]
      (is (match? expected-boundary-amount-score result)))))

;; normalize-installments tests
(s/deftest normalize-installments-normal-test
  (testing "it should return a proportional score when installments is less than max-installments"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:installments normal-installments
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-installments transaction normalization)]
      (is (match? expected-normal-installments-score result)))))

(s/deftest normalize-installments-clamped-high-test
  (testing "it should clamp score to 1.0 when installments exceeds max-installments"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:installments above-max-installments
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-installments transaction normalization)]
      (is (match? expected-clamped-high-installments-score result)))))

(s/deftest normalize-installments-clamped-low-test
  (testing "it should clamp score to 0.0 when installments is negative"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:installments negative-installments
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-installments transaction normalization)]
      (is (match? expected-clamped-low-installments-score result)))))

(s/deftest normalize-installments-boundary-test
  (testing "it should return exactly 1.0 when installments equals max-installments"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:installments boundary-installments
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-installments transaction normalization)]
      (is (match? expected-boundary-installments-score result)))))

;; normalize-amount-vs-avg tests
(s/deftest normalize-amount-vs-avg-normal-test
  (testing "it should return a proportional score when amount / avg-amount is less than ratio"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       amount-for-normal-avg-ratio
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount-vs-avg transaction customer normalization)]
      (is (match? expected-normal-avg-ratio-score result)))))

(s/deftest normalize-amount-vs-avg-clamped-high-test
  (testing "it should clamp score to 1.0 when (amount / avg-amount) / ratio exceeds 1.0"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       amount-for-high-avg-ratio
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount-vs-avg transaction customer normalization)]
      (is (match? expected-clamped-high-avg-ratio-score result)))))

(s/deftest normalize-amount-vs-avg-clamped-low-test
  (testing "it should clamp score to 0.0 when amount is negative"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       amount-for-low-avg-ratio
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount-vs-avg transaction customer normalization)]
      (is (match? expected-clamped-low-avg-ratio-score result)))))

(s/deftest normalize-amount-vs-avg-boundary-test
  (testing "it should return exactly 1.0 when (amount / avg-amount) / ratio equals 1.0"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:amount       amount-for-boundary-avg-ratio
                                                :requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          result (logic.fraud-score/normalize-amount-vs-avg transaction customer normalization)]
      (is (match? expected-boundary-avg-ratio-score result)))))

;; normalize-hour-of-day tests
(s/deftest normalize-hour-of-day-midnight-test
  (testing "it should return 0.0 for midnight (hour 0)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-15T00:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-hour-of-day transaction)]
      (is (match? expected-hour-midnight-score result)))))

(s/deftest normalize-hour-of-day-noon-test
  (testing "it should return proportional score for noon (hour 12)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-15T12:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-hour-of-day transaction)]
      (is (match? expected-hour-noon-score result)))))

(s/deftest normalize-hour-of-day-end-of-day-test
  (testing "it should return 1.0 for end of day (hour 23)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-15T23:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-hour-of-day transaction)]
      (is (match? expected-hour-end-of-day-score result)))))

(s/deftest normalize-hour-of-day-morning-test
  (testing "it should return proportional score for morning (hour 6)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-15T06:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-hour-of-day transaction)]
      (is (match? expected-hour-morning-score result)))))

;; normalize-day-of-week tests
(s/deftest normalize-day-of-week-monday-test
  (testing "it should return 0.0 for Monday (day 0)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-17T00:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-day-of-week transaction)]
      (is (match? expected-day-monday-score result)))))

(s/deftest normalize-day-of-week-saturday-test
  (testing "it should return proportional score for Saturday (day 5)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-15T00:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-day-of-week transaction)]
      (is (match? expected-day-saturday-score result)))))

(s/deftest normalize-day-of-week-sunday-test
  (testing "it should return 1.0 for Sunday (day 6)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-16T00:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-day-of-week transaction)]
      (is (match? expected-day-sunday-score result)))))

(s/deftest normalize-day-of-week-wednesday-test
  (testing "it should return proportional score for Wednesday (day 2)"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-19T00:00:00Z")}
                                               {})
          result (logic.fraud-score/normalize-day-of-week transaction)]
      (is (match? expected-day-wednesday-score result)))))

;; normalize-minutes-since-last-tx tests
(s/deftest normalize-minutes-since-last-tx-nil-test
  (testing "it should return -1 when last-transaction is nil"
    (let [transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at (Instant/parse "2024-06-15T10:30:00Z")}
                                               {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-minutes max-minutes}
                                                        {})
          result (logic.fraud-score/normalize-minutes-since-last-tx transaction nil normalization-config)]
      (is (match? -1 result)))))

(s/deftest normalize-minutes-since-last-tx-proportional-test
  (testing "it should return proportional score when last-transaction is 30 minutes ago"
    (let [current-time (Instant/parse "2024-06-15T10:30:00Z")
          last-tx-time (Instant/parse "2024-06-15T10:00:00Z")
          transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at current-time}
                                               {})
          last-transaction (helpers.schema/generate models.transaction/LastTransaction
                                                    {:timestamp last-tx-time}
                                                    {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-minutes max-minutes}
                                                        {})
          result (logic.fraud-score/normalize-minutes-since-last-tx transaction last-transaction normalization-config)]
      (is (match? expected-minutes-30-score result)))))

(s/deftest normalize-minutes-since-last-tx-clamped-high-test
  (testing "it should clamp to 1.0 when minutes exceed max-minutes"
    (let [current-time (Instant/parse "2024-06-15T12:00:00Z")
          last-tx-time (Instant/parse "2024-06-15T10:00:00Z")
          transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at current-time}
                                               {})
          last-transaction (helpers.schema/generate models.transaction/LastTransaction
                                                    {:timestamp last-tx-time}
                                                    {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-minutes max-minutes}
                                                        {})
          result (logic.fraud-score/normalize-minutes-since-last-tx transaction last-transaction normalization-config)]
      (is (match? expected-minutes-120-score result)))))

(s/deftest normalize-minutes-since-last-tx-clamped-low-test
  (testing "it should clamp to 0.0 when minutes are negative (future timestamp)"
    (let [current-time (Instant/parse "2024-06-15T10:00:00Z")
          last-tx-time (Instant/parse "2024-06-15T10:05:00Z")
          transaction (helpers.schema/generate models.transaction/Transaction
                                               {:requested-at current-time}
                                               {})
          last-transaction (helpers.schema/generate models.transaction/LastTransaction
                                                    {:timestamp last-tx-time}
                                                    {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-minutes max-minutes}
                                                        {})
          result (logic.fraud-score/normalize-minutes-since-last-tx transaction last-transaction normalization-config)]
      (is (match? expected-minutes-negative-score result)))))

;; normalize-km-from-last-tx tests
(s/deftest normalize-km-from-last-tx-nil-test
  (testing "it should return -1 when last-transaction is nil"
    (let [normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-last-tx nil normalization-config)]
      (is (match? -1 result)))))

(s/deftest normalize-km-from-last-tx-proportional-test
  (testing "it should return proportional score when km is 50"
    (let [last-transaction (helpers.schema/generate models.transaction/LastTransaction
                                                    {:km-from-current km-from-current-50}
                                                    {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-last-tx last-transaction normalization-config)]
      (is (match? expected-km-50-score result)))))

(s/deftest normalize-km-from-last-tx-clamped-high-test
  (testing "it should clamp to 1.0 when km exceeds max-km"
    (let [last-transaction (helpers.schema/generate models.transaction/LastTransaction
                                                    {:km-from-current km-from-current-200}
                                                    {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-last-tx last-transaction normalization-config)]
      (is (match? expected-km-200-score result)))))

(s/deftest normalize-km-from-last-tx-clamped-low-test
  (testing "it should clamp to 0.0 when km is 0"
    (let [last-transaction (helpers.schema/generate models.transaction/LastTransaction
                                                    {:km-from-current km-from-current-0}
                                                    {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-last-tx last-transaction normalization-config)]
      (is (match? expected-km-0-score result)))))

;; normalize-km-from-home tests
(s/deftest normalize-km-from-home-zero-test
  (testing "it should return 0.0 when km-from-home is 0"
    (let [terminal (helpers.schema/generate models.terminal/Terminal
                                            {:km-from-home km-from-home-0}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-home terminal normalization-config)]
      (is (match? expected-km-home-0-score result)))))

(s/deftest normalize-km-from-home-proportional-test
  (testing "it should return proportional score when km-from-home is 50"
    (let [terminal (helpers.schema/generate models.terminal/Terminal
                                            {:km-from-home km-from-home-50}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-home terminal normalization-config)]
      (is (match? expected-km-home-50-score result)))))

(s/deftest normalize-km-from-home-at-max-test
  (testing "it should return 1.0 when km-from-home equals max-km"
    (let [terminal (helpers.schema/generate models.terminal/Terminal
                                            {:km-from-home km-from-home-100}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-home terminal normalization-config)]
      (is (match? expected-km-home-100-score result)))))

(s/deftest normalize-km-from-home-over-max-test
  (testing "it should clamp to 1.0 when km-from-home exceeds max-km"
    (let [terminal (helpers.schema/generate models.terminal/Terminal
                                            {:km-from-home km-from-home-200}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-km max-km}
                                                        {})
          result (logic.fraud-score/normalize-km-from-home terminal normalization-config)]
      (is (match? expected-km-home-200-score result)))))

;; normalize-tx-count-24h tests
(s/deftest normalize-tx-count-24h-zero-test
  (testing "it should return 0.0 when tx-count-24h is 0"
    (let [customer-config (helpers.schema/generate models.customer/Customer
                                                   {:tx-count-24h tx-count-0}
                                                   {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-tx-count-24h max-tx-count-24h}
                                                        {})
          result (logic.fraud-score/normalize-tx-count-24h customer-config normalization-config)]
      (is (match? expected-tx-count-0-score result)))))

(s/deftest normalize-tx-count-24h-proportional-test
  (testing "it should return proportional score when tx-count-24h is 5"
    (let [customer-config (helpers.schema/generate models.customer/Customer
                                                   {:tx-count-24h tx-count-5}
                                                   {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-tx-count-24h max-tx-count-24h}
                                                        {})
          result (logic.fraud-score/normalize-tx-count-24h customer-config normalization-config)]
      (is (match? expected-tx-count-5-score result)))))

(s/deftest normalize-tx-count-24h-at-max-test
  (testing "it should return 1.0 when tx-count-24h equals max-tx-count-24h"
    (let [customer-config (helpers.schema/generate models.customer/Customer
                                                   {:tx-count-24h tx-count-10}
                                                   {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-tx-count-24h max-tx-count-24h}
                                                        {})
          result (logic.fraud-score/normalize-tx-count-24h customer-config normalization-config)]
      (is (match? expected-tx-count-10-score result)))))

(s/deftest normalize-tx-count-24h-over-max-test
  (testing "it should clamp to 1.0 when tx-count-24h exceeds max-tx-count-24h"
    (let [customer-config (helpers.schema/generate models.customer/Customer
                                                   {:tx-count-24h tx-count-20}
                                                   {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-tx-count-24h max-tx-count-24h}
                                                        {})
          result (logic.fraud-score/normalize-tx-count-24h customer-config normalization-config)]
      (is (match? expected-tx-count-20-score result)))))

;; normalize-is-online tests
(s/deftest normalize-is-online-true-test
  (testing "it should return 1 when terminal is online"
    (let [result (logic.fraud-score/normalize-is-online terminal-online)]
      (is (match? expected-online-score result)))))

(s/deftest normalize-is-online-false-test
  (testing "it should return 0 when terminal is offline"
    (let [result (logic.fraud-score/normalize-is-online terminal-offline)]
      (is (match? expected-offline-score result)))))

;; normalize-card-present tests
(s/deftest normalize-card-present-true-test
  (testing "it should return 1 when card is present"
    (let [result (logic.fraud-score/normalize-card-present terminal-card-present)]
      (is (match? expected-card-present-score result)))))

(s/deftest normalize-card-present-false-test
  (testing "it should return 0 when card is not present"
    (let [result (logic.fraud-score/normalize-card-present terminal-card-not-present)]
      (is (match? expected-card-not-present-score result)))))

;; normalize-unknown-merchant tests
(s/deftest normalize-unknown-merchant-known-test
  (testing "it should return 0 when merchant is in known-merchants list"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:id merchant-known}
                                            {})
          result (logic.fraud-score/normalize-unknown-merchant merchant customer-with-known-merchants)]
      (is (match? expected-known-merchant-score result)))))

(s/deftest normalize-unknown-merchant-unknown-test
  (testing "it should return 1 when merchant is not in known-merchants list"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:id merchant-unknown}
                                            {})
          result (logic.fraud-score/normalize-unknown-merchant merchant customer-with-known-merchants)]
      (is (match? expected-unknown-merchant-score result)))))

(s/deftest normalize-unknown-merchant-empty-list-test
  (testing "it should return 1 when known-merchants list is empty"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:id merchant-known}
                                            {})
          result (logic.fraud-score/normalize-unknown-merchant merchant customer-with-empty-known-merchants)]
      (is (match? expected-unknown-merchant-score result)))))

;; normalize-merchant-avg-amount tests
(s/deftest normalize-merchant-avg-amount-zero-test
  (testing "it should return 0.0 when avg-amount is 0"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:avg-amount merchant-avg-amount-0}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-merchant-avg-amount max-merchant-avg-amount}
                                                        {})
          result (logic.fraud-score/normalize-merchant-avg-amount merchant normalization-config)]
      (is (match? expected-merchant-avg-0-score result)))))

(s/deftest normalize-merchant-avg-amount-proportional-test
  (testing "it should return proportional score when avg-amount is 500"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:avg-amount merchant-avg-amount-500}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-merchant-avg-amount max-merchant-avg-amount}
                                                        {})
          result (logic.fraud-score/normalize-merchant-avg-amount merchant normalization-config)]
      (is (match? expected-merchant-avg-500-score result)))))

(s/deftest normalize-merchant-avg-amount-at-max-test
  (testing "it should return 1.0 when avg-amount equals max-merchant-avg-amount"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:avg-amount merchant-avg-amount-1000}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-merchant-avg-amount max-merchant-avg-amount}
                                                        {})
          result (logic.fraud-score/normalize-merchant-avg-amount merchant normalization-config)]
      (is (match? expected-merchant-avg-1000-score result)))))

(s/deftest normalize-merchant-avg-amount-over-max-test
  (testing "it should clamp to 1.0 when avg-amount exceeds max-merchant-avg-amount"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:avg-amount merchant-avg-amount-2000}
                                            {})
          normalization-config (helpers.schema/generate models.normalization/Normalization
                                                        {:max-merchant-avg-amount max-merchant-avg-amount}
                                                        {})
          result (logic.fraud-score/normalize-merchant-avg-amount merchant normalization-config)]
      (is (match? expected-merchant-avg-2000-score result)))))

;; normalize-mcc-risk tests
(s/deftest normalize-mcc-risk-known-low-test
  (testing "it should return the table risk score for a known low-risk MCC"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:mcc mcc-known-low}
                                            {})
          result (logic.fraud-score/normalize-mcc-risk merchant mcc-risk)]
      (is (match? expected-mcc-known-low-score result)))))

(s/deftest normalize-mcc-risk-known-high-test
  (testing "it should return the table risk score for a known high-risk MCC"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:mcc mcc-known-high}
                                            {})
          result (logic.fraud-score/normalize-mcc-risk merchant mcc-risk)]
      (is (match? expected-mcc-known-high-score result)))))

(s/deftest normalize-mcc-risk-unknown-test
  (testing "it should return 0.5 as default when MCC is not in the risk table"
    (let [merchant (helpers.schema/generate models.merchant/Merchant
                                            {:mcc mcc-unknown}
                                            {})
          result (logic.fraud-score/normalize-mcc-risk merchant mcc-risk)]
      (is (match? expected-mcc-unknown-score result)))))

;; vectorized test fixtures
(def vectorized-spec-normalization
  (helpers.schema/generate models.normalization/Normalization
                           {:max-amount              10000
                            :max-installments        12
                            :amount-vs-avg-ratio     10
                            :max-minutes             1440
                            :max-km                  1000
                            :max-tx-count-24h        20
                            :max-merchant-avg-amount 10000}
                           {}))

(def vectorized-spec-transaction
  (helpers.schema/generate models.transaction/Transaction
                           {:amount       9505.97
                            :installments 10
                            :requested-at (Instant/parse "2026-03-14T05:15:12Z")}
                           {}))

(def vectorized-spec-customer
  (helpers.schema/generate models.customer/Customer
                           {:avg-amount      81.28
                            :tx-count-24h    20
                            :known-merchants #{"MERC-008" "MERC-007" "MERC-005"}}
                           {}))

(def vectorized-spec-merchant
  (helpers.schema/generate models.merchant/Merchant
                           {:id         "MERC-068"
                            :mcc        "7802"
                            :avg-amount 54.86}
                           {}))

(def vectorized-spec-terminal
  (helpers.schema/generate models.terminal/Terminal
                           {:online?       false
                            :card-present? true
                            :km-from-home  952.27}
                           {}))

(def vectorized-spec-mcc-risk
  {"7802" 0.75})

(s/deftest vectorized-no-last-transaction-test
  (testing "it should return a 14-element vector with nil sentinels at indices 5 and 6 when last-transaction is nil"
    (let [fraud-score (helpers.schema/generate models.fraud-score/FraudScore
                                               {:transaction vectorized-spec-transaction
                                                :customer    vectorized-spec-customer
                                                :merchant    vectorized-spec-merchant
                                                :terminal    vectorized-spec-terminal}
                                               {})
          result (logic.fraud-score/vectorized fraud-score vectorized-spec-normalization vectorized-spec-mcc-risk)]
      (is (= 14 (count result)))
      (is (= -1.0 (nth result 5)))
      (is (= -1.0 (nth result 6)))
      (is (= 0.0 (nth result 9)))
      (is (= 1.0 (nth result 10)))
      (is (= 1.0 (nth result 11)))
      (is (= 0.75 (nth result 12)))
      (is (< (Math/abs (- (nth result 0) 0.950597)) 0.0001))
      (is (< (Math/abs (- (nth result 1) 0.833333)) 0.0001))
      (is (< (Math/abs (- (nth result 2) 1.0)) 0.0001))
      (is (< (Math/abs (- (nth result 3) 0.217391)) 0.0001))
      (is (< (Math/abs (- (nth result 4) 0.833333)) 0.0001))
      (is (< (Math/abs (- (nth result 7) 0.95227)) 0.0001))
      (is (< (Math/abs (- (nth result 8) 1.0)) 0.0001))
      (is (< (Math/abs (- (nth result 13) 0.005486)) 0.0001)))))

(s/deftest vectorized-with-last-transaction-test
  (testing "it should return a 14-element vector with real values at indices 5 and 6 when last-transaction is present"
    (let [fraud-score (helpers.schema/generate models.fraud-score/FraudScore
                                               {:transaction      vectorized-spec-transaction
                                                :customer         vectorized-spec-customer
                                                :merchant         vectorized-spec-merchant
                                                :terminal         vectorized-spec-terminal
                                                :last-transaction {:timestamp      (Instant/parse "2026-03-14T05:00:00Z")
                                                                   :km-from-current 50.0}}
                                               {})
          result (logic.fraud-score/vectorized fraud-score vectorized-spec-normalization vectorized-spec-mcc-risk)]
      (is (= 14 (count result)))
      (is (< (Math/abs (- (nth result 5) 0.010417)) 0.0001))
      (is (< (Math/abs (- (nth result 6) 0.05)) 0.0001))
      (is (= 0.0 (nth result 9)))
      (is (= 1.0 (nth result 10)))
      (is (= 1.0 (nth result 11)))
      (is (= 0.75 (nth result 12)))
      (is (< (Math/abs (- (nth result 0) 0.950597)) 0.0001))
      (is (< (Math/abs (- (nth result 1) 0.833333)) 0.0001))
      (is (< (Math/abs (- (nth result 2) 1.0)) 0.0001))
      (is (< (Math/abs (- (nth result 3) 0.217391)) 0.0001))
      (is (< (Math/abs (- (nth result 4) 0.833333)) 0.0001))
      (is (< (Math/abs (- (nth result 7) 0.95227)) 0.0001))
      (is (< (Math/abs (- (nth result 8) 1.0)) 0.0001))
      (is (< (Math/abs (- (nth result 13) 0.005486)) 0.0001)))))
