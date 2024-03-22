(ns notifications
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:send-message-notifications
          {:fn
           (fn [params req plugin]
             (plugin/notify-message "foo" req plugin)
             (plugin/notify-message "bar" req plugin)
             (plugin/notify-message "baz" req plugin)
             {:foo "bar"})}
          :send-progress-notifications
          {:fn
           (fn [params req plugin] nil)}
          }}))

(plugin/run plugin)
