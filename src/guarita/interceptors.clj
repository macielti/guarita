(ns guarita.interceptors
  (:require [jsonista.core :as j])
  (:import [com.fasterxml.jackson.databind SerializationFeature]
           [com.fasterxml.jackson.datatype.jsr310 JavaTimeModule]))

(def mapper
  (j/object-mapper {:decode-key-fn true
                    :modules       [(JavaTimeModule.)]
                    :features      {SerializationFeature/WRITE_DATES_AS_TIMESTAMPS false}}))

(def body-json-params-interceptor
  {:name  ::jsonista-body-json-params
   :enter (fn [{:keys [request] :as context}]
            (assoc-in context [:request :json-params]
                      (j/read-value (:body request) mapper)))})
