(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]))

(def ^:private k 5)
(def ^:private nprobe-fast 7)
(def ^:private nprobe-full 16)

(defn fraud-score!
  ^double [wire-input {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        ^floats query-arr (logic.fraud-score/vectorized-from-wire wire-input normalization mcc-risk)]
    (/ (double (dataset/knn-ivf-fraud-count dataset query-arr k nprobe-fast nprobe-full)) (double k))))
