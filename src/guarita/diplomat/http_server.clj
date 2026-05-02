(ns guarita.diplomat.http-server
  (:require [guarita.diplomat.http-server.fraud-score :as diplomat.http-server.fraud-score]
            [guarita.diplomat.http-server.ready :as diplomat.http-server.ready]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.service.interceptors :as pedestal.service.interceptors]))

(def routes
  [["/ready" :get [diplomat.http-server.ready/ready]
    :route-name :ready]
   ["/fraud-score"
    :post [(body-params/body-params)
           diplomat.http-server.fraud-score/fraud-score!]
    :route-name :fraud-score]])
