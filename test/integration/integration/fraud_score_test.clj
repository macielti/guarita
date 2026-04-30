(ns integration.fraud-score-test
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer [is]]
            [integrant.core :as ig]
            [integration.aux.components :as aux.components]
            [io.pedestal.connector.test :as connector.test]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]
            [service.component :as component.service]))

(def legit-tx-body
  {:id          "legit-tx-001"
   :transaction {:amount 100.0 :installments 1 :requested_at "2024-06-15T10:30:00Z"}
   :customer    {:avg_amount 500.0 :tx_count_24h 5 :known_merchants ["MERC-001"]}
   :merchant    {:id "MERC-001" :mcc "5411" :avg_amount 200.0}
   :terminal    {:is_online true :card_present true :km_from_home 5.0}})

(def fraud-tx-body
  {:id          "fraud-tx-001"
   :transaction {:amount 9000.0 :installments 12 :requested_at "2024-06-15T03:00:00Z"}
   :customer    {:avg_amount 500.0 :tx_count_24h 20 :known_merchants []}
   :merchant    {:id "UNKN-999" :mcc "7801" :avg_amount 200.0}
   :terminal    {:is_online false :card_present false :km_from_home 1000.0}
   :last_transaction {:timestamp "2024-06-15T00:00:00Z" :km_from_current 500.0}})

(s/deftest fraud-score-legit-test
  (let [system    (aux.components/start-system!)
        connector (-> system ::component.service/service)
        response  (connector.test/response-for connector
                                               :post "/fraud-score"
                                               :headers {"content-type" "application/json"}
                                               :body (cheshire/generate-string legit-tx-body))]
    (is (match? {:status 200} response))
    (is (match? {"approved" true "fraud_score" 0.0}
                (cheshire/parse-string (:body response))))
    (ig/halt! system)))

(s/deftest fraud-score-fraud-test
  (let [system    (aux.components/start-system!)
        connector (-> system ::component.service/service)
        response  (connector.test/response-for connector
                                               :post "/fraud-score"
                                               :headers {"content-type" "application/json"}
                                               :body (cheshire/generate-string fraud-tx-body))]
    (is (match? {:status 200} response))
    (is (match? {"approved" false "fraud_score" 1.0}
                (cheshire/parse-string (:body response))))
    (ig/halt! system)))
