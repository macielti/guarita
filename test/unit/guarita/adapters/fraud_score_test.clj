(ns guarita.adapters.fraud-score-test
  (:require [clojure.test :refer [is testing]]
            [fixtures.fraud-score]
            [guarita.adapters.fraud-score :as adapters.fraud-score]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]))

(s/deftest wire->fraud-score-test
  (testing "it should convert a wire fraud-score to an internal fraud-score with last-transaction"
    (let [result (adapters.fraud-score/wire->fraud-score fixtures.fraud-score/wire-in-fraud-score)]
      (is (match? {:id               "tx-1329056812"
                   :transaction      {:requested-at inst?}
                   :customer         {:avg-amount   number?}
                   :merchant         {:avg-amount   number?}
                   :terminal         {:online?      boolean?}
                   :last-transaction {:timestamp    inst?}}
                  result)))))

(s/deftest wire->fraud-score-no-last-transaction-test
  (testing "it should convert a wire fraud-score to an internal fraud-score without last-transaction"
    (let [result (adapters.fraud-score/wire->fraud-score fixtures.fraud-score/wire-in-fraud-score-no-last-transaction)]
      (is (match? {:id          "tx-1329056812"
                   :transaction {:requested-at inst?}}
                  result)))))
