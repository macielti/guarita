(ns guarita.components
  (:require [common-clj.integrant-components.config :as component.config]
            [common-clj.integrant-components.routes :as component.routes]
            [service.component :as component.service]
            [guarita.diplomat.http-server :as diplomat.http-server]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.tools.logging])
  (:gen-class))

(taoensso.timbre.tools.logging/use-timbre)

(def components
  {:config (ig/ref ::component.config/config)})

(def arranjo
  (merge
    {::component.config/config {:path "resources/config.edn"
                                :env  :prod}}
    {::component.routes/routes {:routes diplomat.http-server/routes}}
    {::component.service/service {:components (merge components
                                                     {:routes (ig/ref ::component.routes/routes)})}}))

(defn start-system! []
  (timbre/set-min-level! :debug)
  (ig/init arranjo))

(def -main start-system!)
