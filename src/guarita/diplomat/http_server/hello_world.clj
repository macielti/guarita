(ns guarita.diplomat.http-server.hello-world
  (:require [schema.core :as s]))

(s/defn hello-world
        [{{:keys [datalevin config]}  :components}]
        {:status 200
         :body   {:hello :world}})
