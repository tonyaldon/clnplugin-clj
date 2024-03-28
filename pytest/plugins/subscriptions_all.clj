(ns subscriptions-all
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:subscriptions
         {:warning
          {:fn (fn [params req plugin]
                 (plugin/log (format "Got a warning notification before the init request: %s" req) plugin))}
          :shutdown
          {:fn (fn [params req plugin]
                 (plugin/log
                  (format "subscriptions.clj plugin shutting down itself")
                  plugin)
                 (shutdown-agents)
                 (System/exit 0))}
          :*
          {:fn (fn [params req plugin]
                 (let [topic (:method req)]
                   (when (= topic "connect")
                     (plugin/log "Got a connect notification" plugin))))}}}))

(plugin/run plugin)
