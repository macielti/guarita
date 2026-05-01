(ns guarita.adapters.customer
  (:require [guarita.models.customer :as models.customer]
            [guarita.wire.in.customer :as wire.in.customer]
            [schema.core :as s]))

(s/defn wire->customer :- models.customer/Customer
  [{:keys [avg_amount tx_count_24h known_merchants]} :- wire.in.customer/Customer]
  {:avg-amount      avg_amount
   :tx-count-24h    tx_count_24h
   :known-merchants (set known_merchants)})
