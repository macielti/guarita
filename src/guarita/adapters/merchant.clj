(ns guarita.adapters.merchant)

(defn wire->merchant
  [{:keys [id mcc avg_amount]}]
  {:id         id
   :mcc        mcc
   :avg-amount avg_amount})
