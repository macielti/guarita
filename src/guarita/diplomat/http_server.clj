(ns guarita.diplomat.http-server
  (:require [guarita.diplomat.http-server.fraud-score :as diplomat.http-server.fraud-score]
            [guarita.diplomat.http-server.ready :as diplomat.http-server.ready]
            [io.pedestal.service.interceptors :as pedestal.service.interceptors]
            [service.interceptors]))

(def routes
  [["/ready" :get [diplomat.http-server.ready/ready]
    :route-name :ready]
   ["/fraud-score"
    :post [pedestal.service.interceptors/json-body
           #_(service.interceptors/wire-in-body-schema wire.in.fraud-score/FraudScore)
           diplomat.http-server.fraud-score/fraud-score!]
    :route-name :fraud-score]])
