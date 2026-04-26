(ns guarita.adapters.merchant
  (:require [guarita.models.merchant :as models.merchant]
            [guarita.wire.in.merchant :as wire.in.merchant]
            [schema.core :as s]))

(s/defn wire->merchant :- models.merchant/Merchant
  [{:keys [id mcc avg-amount]} :- wire.in.merchant/Merchant]
  {:id         id
   :mcc        mcc
   :avg-amount avg-amount})
