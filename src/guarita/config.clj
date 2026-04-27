(ns guarita.config
  (:require [guarita.models.normalization :as models.normalization]
            [guarita.wire.config :as wire.config]
            [schema.core :as s]))

(s/defn normalization :- models.normalization/Normalization
  [config :- wire.config/Config]
  (:normalization config))
