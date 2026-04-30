(ns guarita.wire.in.terminal
  (:require [schema.core :as s]))

(s/defschema Terminal
  {:is_online    s/Bool
   :card_present s/Bool
   :km_from_home s/Num})
