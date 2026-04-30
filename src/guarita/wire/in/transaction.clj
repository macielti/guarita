(ns guarita.wire.in.transaction
  (:require [schema.core :as s]))

(s/defschema Transaction
  {:amount       s/Num
   :installments s/Int
   :requested_at s/Str})

(s/defschema LastTransaction
  {:timestamp       s/Str
   :km_from_current s/Num})
