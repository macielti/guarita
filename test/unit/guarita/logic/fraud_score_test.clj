(ns guarita.logic.fraud-score-test
  (:require [clojure.test :refer [is testing]]
            [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.logic.fraud-score :as logic.fraud-score]
            [guarita.models.customer :as models.customer]
            [guarita.models.normalization :as models.normalization]
            [guarita.models.transaction :as models.transaction]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s])
  (:import [java.time Instant]))

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
