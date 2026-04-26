(ns guarita.models.terminal
  (:require [schema.core :as s]))

(s/defschema Terminal
  {:online?       s/Bool
   :card-present? s/Bool
   :km-from-home  s/Num})
