(ns guarita.adapters.terminal-test
  (:require [clojure.test :refer [is testing]]
            [common-test-clj.helpers.schema :as helpers.schema]
            [guarita.adapters.terminal :as adapters.terminal]
            [guarita.wire.in.terminal :as wire.in.terminal]
            [matcher-combinators.test :refer [match?]]
            [schema.test :as s]))

(def terminal-online? true)
(def terminal-card-present? true)
(def terminal-km-from-home 10.5)

(s/deftest wire->terminal-test
  (testing "it should convert a wire terminal to an internal terminal"
    (let [fixture (helpers.schema/generate wire.in.terminal/Terminal
                                           {:is_online    terminal-online?
                                            :card_present terminal-card-present?
                                            :km_from_home terminal-km-from-home}
                                           {})
          result (adapters.terminal/wire->terminal fixture)]
      (is (match? {:online?       terminal-online?
                   :card-present? terminal-card-present?
                   :km-from-home  terminal-km-from-home}
                  result)))))
