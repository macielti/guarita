(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]))

(def ^:private k 5)
(def ^:private nprobe 16)
(def ^:private threshold 0.6)

(defn fraud-score!
  [input {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        ^floats query-arr (logic.fraud-score/vectorized input normalization mcc-risk)
        fraud-count   (dataset/knn-ivf-fraud-count dataset query-arr k nprobe)
        score         (/ (double fraud-count) k)]
    {:approved    (< score threshold)
     :fraud_score score}))
