(ns guarita.adapters.fraud-score
  (:require [guarita.adapters.customer :as adapters.customer]
            [guarita.adapters.merchant :as adapters.merchant]
            [guarita.adapters.terminal :as adapters.terminal]
            [guarita.adapters.transaction :as adapters.transaction]))

(defn wire->fraud-score
  [{:keys [id transaction customer merchant terminal last_transaction]}]
  (cond-> {:id          id
           :transaction (adapters.transaction/wire->transaction transaction)
           :customer    (adapters.customer/wire->customer customer)
           :merchant    (adapters.merchant/wire->merchant merchant)
           :terminal    (adapters.terminal/wire->terminal terminal)}
    last_transaction (assoc :last-transaction (adapters.transaction/wire->last-transaction last_transaction))))
