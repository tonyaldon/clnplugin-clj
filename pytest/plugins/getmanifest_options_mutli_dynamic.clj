(ns getmanifest-options-multi-dynamic
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:options {:foo {:type "string"
                         :description "foo-description"
                         :multi true
                         :dynamic true}}}))

(plugin/run plugin)
