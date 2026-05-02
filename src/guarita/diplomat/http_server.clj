(ns guarita.diplomat.http-server
  (:require [guarita.diplomat.http-server.fraud-score :as diplomat.http-server.fraud-score]
            [guarita.diplomat.http-server.ready :as diplomat.http-server.ready]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.response :as response]
            [io.pedestal.interceptor :as interceptor])
  (:import (jakarta.servlet.http HttpServletResponse)))

(def wire-ready-terminator
  (interceptor/interceptor
   {:name ::wire-ready-terminator
    :leave (fn [{:keys [^HttpServletResponse servlet-response response] :as ctx}]
             (if-let [{:keys [status headers ^bytes body]} response]
               (if (and servlet-response (bytes? body))
                 (do (when-not (.isCommitted servlet-response)
                       (.setStatus servlet-response status)
                       (.setHeader servlet-response "Content-Type"
                                   (get headers "Content-Type" "application/json"))
                       (doseq [[k v] (dissoc headers "Content-Type")]
                         (.setHeader servlet-response k v))
                       (.write (.getOutputStream servlet-response) body)
                       (.flushBuffer servlet-response))
                     (response/disable-response ctx))
                 ctx)
               ctx))}))

(def routes
  [["/ready" :get [diplomat.http-server.ready/ready]
    :route-name :ready]
   ["/fraud-score"
    :post [(body-params/body-params)
           wire-ready-terminator
           diplomat.http-server.fraud-score/fraud-score!]
    :route-name :fraud-score]])
