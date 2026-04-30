(ns guarita.adapters.transaction-test
  (:require [clojure.test :refer [is testing]]
            [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.adapters.transaction :as adapters.transaction]
            [guarita.wire.in.transaction :as wire.in.transaction]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]))

(def transaction-amount 500.0)
(def transaction-installments 3)

(def last-transaction-km-from-current 15.0)

(s/deftest wire->transaction-test
  (testing "it should convert a wire transaction to an internal transaction"
    (let [fixture (helpers.schema/generate wire.in.transaction/Transaction
                                           {:amount       transaction-amount
                                            :installments transaction-installments
                                            :requested_at "2024-06-15T10:30:00Z"}
                                           {})
          result (adapters.transaction/wire->transaction fixture)]
      (is (match? {:amount       transaction-amount
                   :installments transaction-installments
                   :requested-at inst?}
                  result)))))

(s/deftest wire->last-transaction-test
  (testing "it should convert a wire last-transaction to an internal last-transaction"
    (let [fixture (helpers.schema/generate wire.in.transaction/LastTransaction
                                           {:timestamp       "2024-06-14T08:00:00Z"
                                            :km_from_current last-transaction-km-from-current}
                                           {})
          result (adapters.transaction/wire->last-transaction fixture)]
      (is (match? {:km-from-current last-transaction-km-from-current
                   :timestamp        inst?}
                  result)))))
