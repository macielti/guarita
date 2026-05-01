(ns guarita.models.customer
  (:require [schema.core :as s]))

(s/defschema Customer
  {:avg-amount      s/Num
   :tx-count-24h    s/Int
   :known-merchants #{s/Str}})
