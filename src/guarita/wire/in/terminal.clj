(ns guarita.wire.in.terminal
  (:require [schema.core :as s]))

(s/defschema Terminal
  {:is-online    s/Bool
   :card-present s/Bool
   :km-from-home s/Num})
