(ns integration.aux.components
  (:require [common-clj.integrant-components.config :as component.config]
            [common-clj.integrant-components.routes :as component.routes]
            [guarita.diplomat.http-server :as diplomat.http-server]
            [integrant.core :as ig]
            [service.component :as component.service]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.tools.logging]))

(taoensso.timbre.tools.logging/use-timbre)

(def components-test
  {:config (ig/ref ::component.config/config)})

(def config-test
  (merge
   {::component.config/config  {:path "resources/config.example.edn"
                                :env  :test}}
   {::component.routes/routes  {:routes diplomat.http-server/routes}}
   {::component.service/service {:components (merge components-test
                                                    {:routes (ig/ref ::component.routes/routes)})}}))

(defn start-system! []
  (timbre/set-min-level! :debug)
  (ig/init config-test))
