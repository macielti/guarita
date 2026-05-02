(ns guarita.adapters.transaction
  (:require [guarita.models.transaction :as models.transaction]
            [guarita.wire.in.transaction :as wire.in.transaction]
            [java-time.api :as jt]
            [schema.core :as s]))

(s/defn wire->transaction :- models.transaction/Transaction
  [{:keys [amount installments requested_at]} :- wire.in.transaction/Transaction]
  {:amount       amount
   :installments installments
   :requested-at (jt/instant requested_at)})

(s/defn wire->last-transaction :- models.transaction/LastTransaction
  [{:keys [timestamp km_from_current]} :- wire.in.transaction/LastTransaction]
  {:timestamp       (jt/instant timestamp)
   :km-from-current km_from_current})
