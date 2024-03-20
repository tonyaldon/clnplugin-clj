(ns init-disable-check-opt
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:options
                   {:foo_1
                    {:check-opt
                     (fn [value plugin]
                       (throw (ex-info "Wrong option 'foo_1'" {})))}}}))

(plugin/run plugin)
