(ns guarita.models.transaction
  (:require [schema.core :as s])
  (:import [java.time Instant]))

(s/defschema Transaction
  {:amount       s/Num
   :installments s/Int
   :requested-at Instant})

(s/defschema LastTransaction
  {:timestamp       Instant
   :km-from-current s/Num})
