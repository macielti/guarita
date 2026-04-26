(ns guarita.wire.in.transaction
  (:require [schema.core :as s]))

(s/defschema Transaction
  {:amount       s/Num
   :installments s/Int
   :requested-at s/Str})

(s/defschema LastTransaction
  {:timestamp       s/Str
   :km-from-current s/Num})
