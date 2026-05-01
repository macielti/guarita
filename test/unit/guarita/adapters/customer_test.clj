(ns guarita.adapters.customer-test
  (:require [clojure.test :refer [is testing]]
            [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.adapters.customer :as adapters.customer]
            [guarita.wire.in.customer :as wire.in.customer]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]))

(def customer-avg-amount 200.0)
(def customer-tx-count 5)
(def customer-known-merchants-input ["merchant-1" "merchant-2"])
(def customer-known-merchants #{"merchant-1" "merchant-2"})

(s/deftest wire->customer-test
  (testing "it should convert a wire customer to an internal customer"
    (let [fixture (helpers.schema/generate wire.in.customer/Customer
                                           {:avg_amount      customer-avg-amount
                                            :tx_count_24h    customer-tx-count
                                            :known_merchants customer-known-merchants-input}
                                           {})
          result (adapters.customer/wire->customer fixture)]
      (is (match? {:avg-amount      customer-avg-amount
                   :tx-count-24h    customer-tx-count
                   :known-merchants customer-known-merchants}
                  result)))))
