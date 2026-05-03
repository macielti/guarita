(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]))

(def ^:private k 5)
(def ^:private nprobe 5)

(defn fraud-score!
  "Returns weighted fraud probability in [0.0, 1.0] using inverse-distance weighting."
  ^double [wire-input {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        ^floats query-arr (logic.fraud-score/vectorized-from-wire wire-input normalization mcc-risk)]
    (dataset/knn-ivf-weighted-fraud-score dataset query-arr k nprobe)))
