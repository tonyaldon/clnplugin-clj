(ns init-disable-check-opt-execution-error
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:options
                   {:foo_5
                    {:check-opt (fn [value plugin] (/ 1 0))}}}))

(plugin/run plugin)
