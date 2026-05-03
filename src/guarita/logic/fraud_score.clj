(ns guarita.logic.fraud-score
  (:import [java.time Duration Instant]))

(defn- clamp ^double [^double x]
  (Math/min 1.0 (Math/max 0.0 x)))

(defn normalize-amount
  [{:keys [amount]} {:keys [max-amount]}]
  (clamp (/ amount max-amount)))

(defn normalize-installments
  [{:keys [installments]} {:keys [max-installments]}]
  (clamp (/ installments max-installments)))

(defn normalize-amount-vs-avg
  [{:keys [amount]} {:keys [avg-amount]} {:keys [amount-vs-avg-ratio]}]
  (clamp (/ amount avg-amount amount-vs-avg-ratio)))

(defn normalize-hour-of-day
  [{:keys [requested-at]}]
  (let [^Instant inst requested-at
        hour         (-> inst .getEpochSecond (quot 3600) (mod 24))]
    (/ hour 23.0)))

(defn normalize-day-of-week
  [{:keys [requested-at]}]
  (let [^Instant inst requested-at
        day          (-> inst .getEpochSecond (quot 86400) (+ 3) (mod 7))]
    (/ day 6.0)))

(defn normalize-minutes-since-last-tx
  [{:keys [requested-at]} last-transaction {:keys [max-minutes]}]
  (if (nil? last-transaction)
    -1
    (let [^Duration dur (Duration/between (:timestamp last-transaction) requested-at)
          minutes       (.toMinutes dur)]
      (clamp (/ minutes max-minutes)))))

(defn normalize-km-from-last-tx
  [last-transaction {:keys [max-km]}]
  (if (nil? last-transaction)
    -1
    (clamp (/ (:km-from-current last-transaction) max-km))))

(defn normalize-km-from-home
  [{:keys [km-from-home]} {:keys [max-km]}]
  (clamp (/ km-from-home max-km)))

(defn normalize-tx-count-24h
  [{:keys [tx-count-24h]} {:keys [max-tx-count-24h]}]
  (clamp (/ tx-count-24h max-tx-count-24h)))

(defn normalize-is-online
  [{:keys [online?]}]
  (if online? 1 0))

(defn normalize-card-present
  [{:keys [card-present?]}]
  (if card-present? 1 0))

(defn normalize-unknown-merchant
  [{:keys [id]} {:keys [known-merchants]}]
  (if (contains? known-merchants id) 0 1))

(defn normalize-merchant-avg-amount
  [{:keys [avg-amount]} {:keys [max-merchant-avg-amount]}]
  (clamp (/ avg-amount max-merchant-avg-amount)))

(defn- mcc-risk-score [mcc mcc-risk]
  (get mcc-risk mcc 0.5))

(defn normalize-mcc-risk
  [{:keys [mcc]} mcc-risk]
  (mcc-risk-score mcc mcc-risk))

(defn vectorized
  [{:keys [transaction customer merchant terminal last-transaction]}
   normalization mcc-risk]
  (float-array
   [(normalize-amount transaction normalization)
    (normalize-installments transaction normalization)
    (normalize-amount-vs-avg transaction customer normalization)
    (normalize-hour-of-day transaction)
    (normalize-day-of-week transaction)
    (normalize-minutes-since-last-tx transaction last-transaction normalization)
    (normalize-km-from-last-tx last-transaction normalization)
    (normalize-km-from-home terminal normalization)
    (normalize-tx-count-24h customer normalization)
    (normalize-is-online terminal)
    (normalize-card-present terminal)
    (normalize-unknown-merchant merchant customer)
    (normalize-mcc-risk merchant mcc-risk)
    (normalize-merchant-avg-amount merchant normalization)]))
