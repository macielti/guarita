(ns guarita.adapters.transaction
  (:require [guarita.models.transaction :as models.transaction]
            [guarita.wire.in.transaction :as wire.in.transaction]
            [schema.core :as s])
  (:import [java.time Instant]
           [java.time.format DateTimeFormatter]))

(def instant-formatter DateTimeFormatter/ISO_INSTANT)

(s/defn wire->transaction :- models.transaction/Transaction
  [{:keys [amount installments requested_at]} :- wire.in.transaction/Transaction]
  {:amount       amount
   :installments installments
   :requested-at (Instant/from (.parse instant-formatter requested_at))})

(s/defn wire->last-transaction :- models.transaction/LastTransaction
  [{:keys [timestamp km_from_current]} :- wire.in.transaction/LastTransaction]
  {:timestamp       (Instant/from (.parse instant-formatter timestamp))
   :km-from-current km_from_current})
