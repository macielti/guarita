(ns guarita.wire.config
  (:require [schema.core :as s]))

(def normalization
  {:max-amount              s/Int
   :max-installments        s/Int
   :amount-vs-avg-ratio     s/Int
   :max-minutes             s/Int
   :max-km                  s/Int
   :max-tx-count-24h        s/Int
   :max-merchant-avg-amount s/Int})

(def mcc-risk
  {s/Str s/Num})

(s/defschema Config
  {:normalization normalization
   :mcc-risk      mcc-risk
   s/Keyword      s/Any})
