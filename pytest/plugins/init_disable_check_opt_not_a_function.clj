(ns init-disable-check-opt-not-a-function
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:options
                   {:foo_3
                    {:check-opt [:a-vector "is not a function"]}}}))

(plugin/run plugin)
