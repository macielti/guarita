(ns guarita.adapters.transaction
  (:import [java.time Instant]))

(defn wire->transaction
  [{:keys [amount installments requested_at]}]
  {:amount       amount
   :installments installments
   :requested-at (Instant/parse ^CharSequence requested_at)})

(defn wire->last-transaction
  [{:keys [timestamp km_from_current]}]
  {:timestamp       (Instant/parse ^CharSequence timestamp)
   :km-from-current km_from_current})
