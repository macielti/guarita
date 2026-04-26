(ns guarita.adapters.merchant-test
  (:require [clojure.test :refer [is testing]]
            [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.adapters.merchant :as adapters.merchant]
            [guarita.wire.in.merchant :as wire.in.merchant]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]))

(def merchant-id "merchant-1")
(def merchant-mcc "5411")
(def merchant-avg-amount 150.0)

(s/deftest wire->merchant-test
  (testing "it should convert a wire merchant to an internal merchant"
    (let [fixture (helpers.schema/generate wire.in.merchant/Merchant
                                           {:id         merchant-id
                                            :mcc        merchant-mcc
                                            :avg-amount merchant-avg-amount}
                                           {})
          result (adapters.merchant/wire->merchant fixture)]
      (is (match? {:id         merchant-id
                   :mcc        merchant-mcc
                   :avg-amount merchant-avg-amount}
                  result)))))
