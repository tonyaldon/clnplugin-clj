(ns hooks-peer-connected-bar
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:hooks
         {:peer_connected
          {:fn (fn [params req plugin]
                 (plugin/log (format "timestamp: %s" (System/currentTimeMillis))
                             plugin)
                 {:result "continue"}
                 ;; (spit "/tmp/foo" "foo bar baz")
                 ;; {:result "disconnect"
                 ;;  :error_message "foo bar baz"}
                 )}}}))


(plugin/run plugin)
