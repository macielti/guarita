(ns guarita.models.mcc
  (:require [schema.core :as s]))

(s/defschema MccRisk
  {s/Str s/Num})