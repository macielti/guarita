(ns guarita.diplomat.http-server.ready
  (:require [schema.core :as s]))

(s/defn ready
  [_request]
  {:status 200
   :body   {:status :ok}})
