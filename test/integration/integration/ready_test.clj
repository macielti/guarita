(ns integration.ready-test
  (:require [clojure.test :refer [is testing]]
            [integrant.core :as ig]
            [integration.aux.components :as aux.components]
            [io.pedestal.connector.test :as connector.test]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]
            [service.component :as component.service]))

(s/deftest ready-test
  (let [system    (aux.components/start-system!)
        connector (-> system ::component.service/service)]

    (testing "GET /ready returns 200"
      (is (match? {:status 200}
                  (connector.test/response-for connector :get "/ready"))))

    (ig/halt! system)))
