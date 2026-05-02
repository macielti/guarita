(ns guarita.diplomat.http-server
  (:require [guarita.diplomat.http-server.fraud-score :as diplomat.http-server.fraud-score]
            [guarita.diplomat.http-server.ready :as diplomat.http-server.ready]
            [io.pedestal.http.body-params :as body-params]
            [service.interceptors :as interceptors]))

(defn make-routes [components]
  [["/ready" :get [diplomat.http-server.ready/ready]
    :route-name :ready]
   ["/fraud-score"
    :post [interceptors/error-handler-interceptor
           (interceptors/components-interceptor components)
           (body-params/body-params)
           diplomat.http-server.fraud-score/fraud-score!]
    :route-name :fraud-score]])
