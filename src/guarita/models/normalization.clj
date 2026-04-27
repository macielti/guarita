(ns guarita.models.normalization
  (:require [schema.core :as s]))

(s/defschema Normalization
  {:max-amount              s/Int
   :max-installments        s/Int
   :amount-vs-avg-ratio     s/Int
   :max-minutes             s/Int
   :max-km                  s/Int
   :max-tx-count-24h        s/Int
   :max-merchant-avg-amount s/Int})
