(ns guarita.adapters.transaction
  (:require [java-time.api :as jt]))

(defn wire->transaction
  [{:keys [amount installments requested_at]}]
  {:amount       amount
   :installments installments
   :requested-at (jt/instant requested_at)})

(defn wire->last-transaction
  [{:keys [timestamp km_from_current]}]
  {:timestamp       (jt/instant timestamp)
   :km-from-current km_from_current})
