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
   {:keys [max-amount max-installments amount-vs-avg-ratio max-minutes max-km
           max-tx-count-24h max-merchant-avg-amount]}
   mcc-risk]
  (let [tx-amount       (double (:amount transaction))
        tx-installments (double (:installments transaction))
        ^Instant tx-ts  (:requested-at transaction)
        cust-avg-amt    (double (:avg-amount customer))
        cust-tx-count   (double (:tx-count-24h customer))
        known-merchants (:known-merchants customer)
        merch-id        (:id merchant)
        merch-mcc       (:mcc merchant)
        merch-avg-amt   (double (:avg-amount merchant))
        term-online?    (:online? terminal)
        term-card?      (:card-present? terminal)
        term-km-home    (double (:km-from-home terminal))
        epoch-sec       (.getEpochSecond tx-ts)
        hour            (mod (quot epoch-sec 3600) 24)
        day             (mod (+ (quot epoch-sec 86400) 3) 7)]
    (float-array
     [(clamp (/ tx-amount max-amount))
      (clamp (/ tx-installments max-installments))
      (clamp (/ tx-amount cust-avg-amt amount-vs-avg-ratio))
      (/ (double hour) 23.0)
      (/ (double day) 6.0)
      (if (nil? last-transaction)
        -1.0
        (clamp (/ (.toMinutes ^Duration (Duration/between (:timestamp last-transaction) tx-ts))
                  max-minutes)))
      (if (nil? last-transaction)
        -1.0
        (clamp (/ (double (:km-from-current last-transaction)) max-km)))
      (clamp (/ term-km-home max-km))
      (clamp (/ cust-tx-count max-tx-count-24h))
      (if term-online? 1.0 0.0)
      (if term-card? 1.0 0.0)
      (if (contains? known-merchants merch-id) 0.0 1.0)
      (double (get mcc-risk merch-mcc 0.5))
      (clamp (/ merch-avg-amt max-merchant-avg-amount))])))

(defn vectorized-from-wire
  "Compute feature vector directly from raw JSON-parsed wire map, skipping adapter allocation."
  [{:keys [transaction customer merchant terminal last_transaction]}
   {:keys [max-amount max-installments amount-vs-avg-ratio max-minutes max-km
           max-tx-count-24h max-merchant-avg-amount]}
   mcc-risk]
  (let [tx-amount       (double (:amount transaction))
        tx-installments (double (:installments transaction))
        ^Instant tx-ts  (Instant/parse ^CharSequence (:requested_at transaction))
        cust-avg-amt    (double (:avg_amount customer))
        cust-tx-count   (double (:tx_count_24h customer))
        ^java.util.Collection known-merchants (:known_merchants customer)
        merch-id        (:id merchant)
        merch-mcc       (:mcc merchant)
        merch-avg-amt   (double (:avg_amount merchant))
        term-online?    (:is_online terminal)
        term-card?      (:card_present terminal)
        term-km-home    (double (:km_from_home terminal))
        epoch-sec       (.getEpochSecond tx-ts)
        hour            (mod (quot epoch-sec 3600) 24)
        day             (mod (+ (quot epoch-sec 86400) 3) 7)]
    (float-array
     [(clamp (/ tx-amount max-amount))
      (clamp (/ tx-installments max-installments))
      (clamp (/ tx-amount cust-avg-amt amount-vs-avg-ratio))
      (/ (double hour) 23.0)
      (/ (double day) 6.0)
      (if (nil? last_transaction)
        -1.0
        (clamp (/ (.toMinutes ^Duration (Duration/between (Instant/parse ^CharSequence (:timestamp last_transaction)) tx-ts))
                  max-minutes)))
      (if (nil? last_transaction)
        -1.0
        (clamp (/ (double (:km_from_current last_transaction)) max-km)))
      (clamp (/ term-km-home max-km))
      (clamp (/ cust-tx-count max-tx-count-24h))
      (if term-online? 1.0 0.0)
      (if term-card? 1.0 0.0)
      (if (.contains known-merchants merch-id) 0.0 1.0)
      (double (get mcc-risk merch-mcc 0.5))
      (clamp (/ merch-avg-amt max-merchant-avg-amount))])))
