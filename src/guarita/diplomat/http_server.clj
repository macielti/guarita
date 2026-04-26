(ns guarita.diplomat.http-server
        (:require [io.pedestal.service.interceptors :as pedestal.service.interceptors]
                  [guarita.diplomat.http-server.hello-world :as diplomat.http-server.hello-world]))

(def routes [["/api/hello-world" :post [pedestal.service.interceptors/json-body
                                        diplomat.http-server.hello-world/hello-world]
              :route-name :hello-world]])
