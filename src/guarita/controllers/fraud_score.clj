(ns guarita.controllers.fraud-score
  (:require [guarita.config :as config]
            [guarita.dataset :as dataset]
            [guarita.logic.fraud-score :as logic.fraud-score]))

(def ^:private k 5)

;; IVF_NPROBE controls how many clusters are scanned in the fast probe.
;; Borderline queries (count near the decision threshold) additionally
;; run a bbox repair pass over unvisited clusters. Default: 7.
(def ^:private nprobe-fast
  (let [v (System/getenv "IVF_NPROBE")]
    (if v (Long/parseLong v) 7)))

(defn fraud-score!
  ^double [wire-input {:keys [config dataset]}]
  (let [normalization (config/normalization config)
        mcc-risk      (config/mcc-risk config)
        ^floats query-arr (logic.fraud-score/vectorized-from-wire wire-input normalization mcc-risk)]
    (/ (double (dataset/knn-ivf-fraud-count dataset query-arr k nprobe-fast)) (double k))))
