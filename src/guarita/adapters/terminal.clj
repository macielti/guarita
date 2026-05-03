(ns guarita.adapters.terminal)

(defn wire->terminal
  [{:keys [is_online card_present km_from_home]}]
  {:online?       is_online
   :card-present? card_present
   :km-from-home  km_from_home})
