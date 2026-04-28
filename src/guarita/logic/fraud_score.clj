(ns guarita.logic.fraud-score
  (:require [guarita.models.customer :as models.customer]
            [guarita.models.normalization :as models.normalization]
            [guarita.models.transaction :as models.transaction]
            [schema.core :as s]))

(defn- clamp [x]
  (-> x double (max 0.0) (min 1.0)))

(s/defn normalize-amount :- s/Num
  [{:keys [amount]} :- models.transaction/Transaction
   {:keys [max-amount]} :- models.normalization/Normalization]
  (clamp (/ amount max-amount)))

(s/defn normalize-installments :- s/Num
  [{:keys [installments]} :- models.transaction/Transaction
   {:keys [max-installments]} :- models.normalization/Normalization]
  (clamp (/ installments max-installments)))

(s/defn normalize-amount-vs-avg :- s/Num
  [{:keys [amount]} :- models.transaction/Transaction
   {:keys [avg-amount]} :- models.customer/Customer
   {:keys [amount-vs-avg-ratio]} :- models.normalization/Normalization]
  (clamp (/ amount avg-amount amount-vs-avg-ratio)))
