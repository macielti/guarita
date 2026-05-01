(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]
            [guarita.models.fraud-score :as models.fraud-score]
            [schema.core :as s]))

(def ^:private k 5)
(def ^:private nprobe 8)
(def ^:private threshold 0.6)

(s/defn fraud-score! :- models.fraud-score/FraudScoreResult
  [input :- models.fraud-score/FraudScore
   {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        query         (-> (logic.fraud-score/vectorized input normalization mcc-risk)
                          float-array)
        neighbors     (dataset/knn-ivf dataset query k nprobe)
        fraud-count   (count (filter #(= :fraud (:label %)) neighbors))
        score         (/ (double fraud-count) k)]
    {:approved    (< score threshold)
     :fraud_score score}))
