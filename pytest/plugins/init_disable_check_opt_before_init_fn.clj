(ns init-disable-check-opt-before-init-fn
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:options
                   {:foo_2
                    {:check-opt
                     (fn [value plugin]
                       (throw (ex-info "Wrong option 'foo_2'" {})))}}}))

(plugin/run plugin)
