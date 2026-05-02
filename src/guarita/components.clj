(ns guarita.components
  (:require [clj-async-profiler.core :as prof]
            [common-clj.integrant-components.config :as component.config]
            [common-clj.integrant-components.routes :as component.routes]
            [guarita.dataset]
            [guarita.diplomat.http-server :as diplomat.http-server]
            [integrant.core :as ig]
            [service.component :as component.service]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.tools.logging])
  (:gen-class))

(taoensso.timbre.tools.logging/use-timbre)

(def components
  {:config  (ig/ref ::component.config/config)
   :dataset (ig/ref :guarita.dataset/dataset)})

(def arranjo
  (merge
    {::component.config/config {:path "resources/config.edn"
                                :env  :prod}}
    {:guarita.dataset/dataset {:vectors-path "resources/vectors.bin"
                               :labels-path  "resources/labels.bin"
                               :ivf-path     "resources/ivf.bin"}}
    {::component.routes/routes {:routes diplomat.http-server/routes}}
    {::component.service/service {:components (merge components
                                                     {:routes (ig/ref ::component.routes/routes)})}}))

(defn start-system! []
  (timbre/set-min-level! :debug)
  (ig/init arranjo))

(defn -main [& _args]
  (let [system (start-system!)]
    #_(prof/serve-ui 8080)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! system)))))
