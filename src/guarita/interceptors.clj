(ns guarita.interceptors
  (:require [jsonista.core :as j]))

(def mapper
  (j/object-mapper {:decode-key-fn true
                    :modules       []}))

(def body-json-params-interceptor
  {:name  ::jsonista-body-json-params
   :enter (fn [{:keys [request] :as context}]
            (assoc-in context [:request :json-params]
                      (j/read-value (:body request) mapper)))})
