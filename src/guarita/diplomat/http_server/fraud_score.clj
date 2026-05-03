(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [guarita.controllers.fraud-score :as controllers.fraud-score]))

;; k=5 → fraud-count ∈ {0,1,2,3,4,5}; threshold=0.6 → approved if count < 3
;; Precompute all 6 possible full HTTP response maps to avoid any per-request allocation.
(def ^:private responses
  (let [k 5 threshold 0.6]
    (into-array
     (map (fn [n]
            (let [score    (/ (double n) k)
                  approved (< score threshold)
                  body     ^"[B" (.getBytes (str "{\"approved\":" approved ",\"fraud_score\":" score "}") "UTF-8")]
              {:status  200
               :headers {"Content-Type"   "application/json"
                         "Content-Length" (str (alength body))}
               :body    body}))
          (range (inc k))))))

(defn fraud-score!
  [{:keys [json-params components]}]
  (let [fraud-count (-> (adapters.fraud-score/wire->fraud-score json-params)
                        (controllers.fraud-score/fraud-score! components))]
    (aget responses fraud-count)))
