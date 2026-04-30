(ns guarita.wire.in.customer
  (:require [schema.core :as s]))

(s/defschema Customer
  {:avg_amount      s/Num
   :tx_count_24h    s/Int
   :known_merchants [s/Str]})
