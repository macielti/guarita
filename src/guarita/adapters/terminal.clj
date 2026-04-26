(ns guarita.adapters.terminal
  (:require [guarita.models.terminal :as models.terminal]
            [guarita.wire.in.terminal :as wire.in.terminal]
            [schema.core :as s]))

(s/defn wire->terminal :- models.terminal/Terminal
  [{:keys [is-online card-present km-from-home]} :- wire.in.terminal/Terminal]
  {:online?       is-online
   :card-present? card-present
   :km-from-home  km-from-home})
