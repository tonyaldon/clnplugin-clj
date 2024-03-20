(ns init-disable-check-opt-symbol-not-a-function
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:options
                   {:foo_4
                    {:check-opt 'some-symbol}}}))

(plugin/run plugin)
