(ns deprecated-options
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:options {:foo_deprecated {:type "string"
                                    :description "foo-description"
                                    :deprecated true}}
         :rpcmethods
         {:get-foo_deprecated-value
          {:fn (fn [plugin params]
                 {:foo_deprecated (get-in @plugin [:options :foo_deprecated :value])})}}}))

(plugin/run plugin)
