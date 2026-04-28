(ns guarita.logic.fraud-score
  (:require [guarita.models.customer :as models.customer]
            [guarita.models.merchant :as models.merchant]
            [guarita.models.normalization :as models.normalization]
            [guarita.models.terminal :as models.terminal]
            [guarita.models.transaction :as models.transaction]
            [schema.core :as s])
  (:import [java.time Duration ZoneOffset]))

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

(s/defn normalize-hour-of-day :- s/Num
  [{:keys [requested-at]} :- models.transaction/Transaction]
  (let [hour (-> requested-at
                 (.atOffset ZoneOffset/UTC)
                 (.getHour))]
    (/ hour 23.0)))

(s/defn normalize-day-of-week :- s/Num
  [{:keys [requested-at]} :- models.transaction/Transaction]
  (let [day (-> requested-at
                (.atOffset ZoneOffset/UTC)
                (.getDayOfWeek)
                (.getValue)
                dec)]
    (/ day 6.0)))

(s/defn normalize-minutes-since-last-tx :- s/Num
  [{:keys [requested-at]} :- models.transaction/Transaction
   last-transaction       :- (s/maybe models.transaction/LastTransaction)
   {:keys [max-minutes]}  :- models.normalization/Normalization]
  (if (nil? last-transaction)
    -1
    (let [minutes (-> (Duration/between (:timestamp last-transaction) requested-at)
                      (.toMinutes))]
      (clamp (/ minutes max-minutes)))))

(s/defn normalize-km-from-last-tx :- s/Num
  [last-transaction :- (s/maybe models.transaction/LastTransaction)
   {:keys [max-km]} :- models.normalization/Normalization]
  (if (nil? last-transaction)
    -1
    (clamp (/ (:km-from-current last-transaction) max-km))))

(s/defn normalize-km-from-home :- s/Num
  [{:keys [km-from-home]} :- models.terminal/Terminal
   {:keys [max-km]}       :- models.normalization/Normalization]
  (clamp (/ km-from-home max-km)))

(s/defn normalize-tx-count-24h :- s/Num
  [{:keys [tx-count-24h]}    :- models.customer/Customer
   {:keys [max-tx-count-24h]} :- models.normalization/Normalization]
  (clamp (/ tx-count-24h max-tx-count-24h)))

(s/defn normalize-is-online :- s/Num
  [{:keys [online?]} :- models.terminal/Terminal]
  (if online? 1 0))

(s/defn normalize-card-present :- s/Num
  [{:keys [card-present?]} :- models.terminal/Terminal]
  (if card-present? 1 0))

(s/defn normalize-unknown-merchant :- s/Num
  [{:keys [id]}              :- models.merchant/Merchant
   {:keys [known-merchants]} :- models.customer/Customer]
  (if (some #{id} known-merchants) 0 1))

(s/defn normalize-merchant-avg-amount :- s/Num
  [{:keys [avg-amount]}           :- models.merchant/Merchant
   {:keys [max-merchant-avg-amount]} :- models.normalization/Normalization]
  (clamp (/ avg-amount max-merchant-avg-amount)))
