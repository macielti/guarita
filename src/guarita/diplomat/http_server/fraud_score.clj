(ns guarita.diplomat.http-server.fraud-score
  (:require [guarita.adapters.fraud-score :as adapters.fraud-score]
            [guarita.controllers.fraud-score :as controllers.fraud-score]))

;; k=5 → fraud-count ∈ {0,1,2,3,4,5}; threshold=0.6 → approved if count < 3
;; Precompute all 6 possible JSON response bodies and their Content-Length strings.
(def ^:private ^objects response-bodies
  (let [k 5 threshold 0.6]
    (into-array
     (map (fn [n]
            (let [score    (/ (double n) k)
                  approved (< score threshold)]
              (.getBytes (str "{\"approved\":" approved ",\"fraud_score\":" score "}") "UTF-8")))
          (range (inc k))))))

(def ^:private ^objects response-lengths
  (into-array (map #(str (alength ^"[B" (aget response-bodies %))) (range 6))))

(defn fraud-score!
  [{:keys [json-params components]}]
  (let [fraud-count (-> (adapters.fraud-score/wire->fraud-score json-params)
                        (controllers.fraud-score/fraud-score! components))
        body        ^"[B" (aget response-bodies fraud-count)]
    {:status  200
     :headers {"Content-Type"   "application/json"
               "Content-Length" (aget response-lengths fraud-count)}
     :body    body}))
