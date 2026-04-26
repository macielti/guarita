(ns guarita.diplomat.http-server
  (:require [guarita.diplomat.http-server.ready :as diplomat.http-server.ready]))

(def routes
  [["/ready" :get [diplomat.http-server.ready/ready]
    :route-name :ready]])
