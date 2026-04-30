(ns guarita.adapters.terminal
  (:require [guarita.models.terminal :as models.terminal]
            [guarita.wire.in.terminal :as wire.in.terminal]
            [schema.core :as s]))

(s/defn wire->terminal :- models.terminal/Terminal
  [{:keys [is_online card_present km_from_home]} :- wire.in.terminal/Terminal]
  {:online?       is_online
   :card-present? card_present
   :km-from-home  km_from_home})
