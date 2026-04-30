(ns guarita.wire.in.merchant
  (:require [schema.core :as s]))

(s/defschema Merchant
  {:id         s/Str
   :mcc        s/Str
   :avg_amount s/Num})
