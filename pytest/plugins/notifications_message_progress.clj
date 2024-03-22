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
           (fn [params req plugin]
             (plugin/notify-progress 0 3 req plugin)
             (plugin/notify-progress 1 3 req plugin)
             (plugin/notify-progress 2 3 req plugin)
             {:foo "bar"})}
          :send-progress-notifications-with-stages
          {:fn
           (fn [params req plugin]
             (plugin/notify-progress 0 2 0 3 req plugin)
             (plugin/notify-progress 1 2 0 3 req plugin)
             (plugin/notify-progress 0 2 1 3 req plugin)
             (plugin/notify-progress 1 2 1 3 req plugin)
             (plugin/notify-progress 0 2 2 3 req plugin)
             (plugin/notify-progress 1 2 2 3 req plugin)
             {:foo "bar"})}
          :wrong-args-in-notify-progress
          {:fn
           (fn [params req plugin]
             ;; step must be < to total-steps
             (plugin/notify-progress 3 3 req plugin)
             )}
          }}))

(plugin/run plugin)
