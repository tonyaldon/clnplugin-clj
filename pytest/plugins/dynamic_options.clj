(ns dynamic-options
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:options {:foo-dynamic {:type "string"
                                 :description "foo-description"
                                 :dynamic true}}
         :rpcmethods
         {:get-foo-dynamic-value
          {:fn (fn [params plugin]
                 {:foo-dynamic (get-in @plugin [:options :foo-dynamic :value])})}}}))

(plugin/run plugin)
