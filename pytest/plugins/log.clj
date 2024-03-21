(ns log
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:log-info
          {:fn (fn [params req plugin]
                 (plugin/log "logged by 'log-info'" plugin))}
          :log-debug
          {:fn (fn [params req plugin]
                 (plugin/log "logged by 'log-debug'" "debug" plugin))}
          :log-multi-lines
          {:fn (fn [params req plugin]
                 (let [message (str "line 0 - logged by 'log-multi-lines'\n"
                                    "line 1 - logged by 'log-multi-lines'\n"
                                    "line 2 - logged by 'log-multi-lines'")]
                   (plugin/log message plugin)))}
          }}))

(plugin/run plugin)
