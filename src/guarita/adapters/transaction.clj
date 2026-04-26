(ns guarita.adapters.transaction
  (:require [guarita.models.transaction :as models.transaction]
            [guarita.wire.in.transaction :as wire.in.transaction]
            [schema.core :as s])
  (:import [java.time Instant]))

(s/defn wire->transaction :- models.transaction/Transaction
  [{:keys [amount installments requested-at]} :- wire.in.transaction/Transaction]
  {:amount       amount
   :installments installments
   :requested-at (Instant/parse requested-at)})

(s/defn wire->last-transaction :- models.transaction/LastTransaction
  [{:keys [timestamp km-from-current]} :- wire.in.transaction/LastTransaction]
  {:timestamp       (Instant/parse timestamp)
   :km-from-current km-from-current})
