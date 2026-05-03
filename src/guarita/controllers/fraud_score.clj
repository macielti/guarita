(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]))

(def ^:private k 5)
(def ^:private nprobe 6)

(defn fraud-score!
  "Returns the raw fraud count (0–k) so the caller can do a direct array lookup."
  ^long [wire-input {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        ^floats query-arr (logic.fraud-score/vectorized-from-wire wire-input normalization mcc-risk)]
    (dataset/knn-ivf-fraud-count dataset query-arr k nprobe)))
