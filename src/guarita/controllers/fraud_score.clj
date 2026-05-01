(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]
            [guarita.models.fraud-score :as models.fraud-score]
            [schema.core :as s]))

(def ^:private k 5)
(def ^:private nprobe 4)
(def ^:private threshold 0.6)

(def ^:private cached-knn-ivf
  (memoize (fn [dataset query-vec k nprobe]
             (dataset/knn-ivf dataset (float-array query-vec) k nprobe))))

(s/defn fraud-score! :- models.fraud-score/FraudScoreResult
  [input :- models.fraud-score/FraudScore
   {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        query-vec     (vec (logic.fraud-score/vectorized input normalization mcc-risk))
        neighbors     (cached-knn-ivf dataset query-vec k nprobe)
        fraud-count   (reduce (fn [n {:keys [label]}] (if (= :fraud label) (inc n) n)) 0 neighbors)
        score         (/ (double fraud-count) k)]
    {:approved    (< score threshold)
     :fraud_score score}))
