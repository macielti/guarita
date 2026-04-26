(ns guarita.models.merchant
  (:require [schema.core :as s]))

(s/defschema Merchant
  {:id         s/Str
   :mcc        s/Str
   :avg-amount s/Num})
