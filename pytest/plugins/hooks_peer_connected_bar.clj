(ns hooks-peer-connected-bar
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:hooks
         {:peer_connected
          {:fn (fn [params req plugin]
                 (plugin/log (format "timestamp: %s" (System/currentTimeMillis))
                             plugin)
                 {:result "continue"})}}}))


(plugin/run plugin)
