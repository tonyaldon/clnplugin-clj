(ns hooks-peer-connected-baz
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:hooks
         {:peer_connected
          {:after ["hooks_peer_connected_bar"]
           :fn (fn [params req plugin]
                 (plugin/log (format "timestamp: %s" (System/currentTimeMillis))
                             plugin)
                 {:result "continue"})}}}))


(plugin/run plugin)
