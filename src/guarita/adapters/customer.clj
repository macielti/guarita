(ns guarita.adapters.customer)

(defn wire->customer
  [{:keys [avg_amount tx_count_24h known_merchants]}]
  {:avg-amount      avg_amount
   :tx-count-24h    tx_count_24h
   :known-merchants (set known_merchants)})
