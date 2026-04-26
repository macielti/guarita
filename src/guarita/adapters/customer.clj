(ns guarita.adapters.customer
  (:require [guarita.models.customer :as models.customer]
            [guarita.wire.in.customer :as wire.in.customer]
            [schema.core :as s]))

(s/defn wire->customer :- models.customer/Customer
  [{:keys [avg-amount tx-count-24h known-merchants]} :- wire.in.customer/Customer]
  {:avg-amount      avg-amount
   :tx-count-24h    tx-count-24h
   :known-merchants known-merchants})
