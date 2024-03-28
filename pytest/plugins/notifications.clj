(ns notifications
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:notify-topic-undeclared-0
          {:fn
           (fn [params req plugin]
             (plugin/notify "topic-undeclared-0" "some params" plugin))}
          :notify-topic-0
          {:fn
           (fn [params req plugin]
             (plugin/notify "topic-0" {:foo-0 "bar-0"} plugin))}
          :notify-topic-1
          {:fn
           (fn [params req plugin]
             (plugin/notify "topic-1" "topic-1 params" plugin))}
          :notify-topic-1-non-json-writable
          {:fn
           (fn [params req plugin]
             (plugin/notify "topic-1" (atom 1) plugin))}}
         :notifications ["topic-0" "topic-1"]
         }))

(plugin/run plugin)
