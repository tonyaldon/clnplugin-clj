(ns log
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:log-info
          {:fn (fn [params plugin]
                 (plugin/log plugin "logged by 'log-info'"))}
          :log-debug
          {:fn (fn [params plugin]
                 (plugin/log plugin "logged by 'log-debug'" "debug"))}
          :log-multi-lines
          {:fn (fn [params plugin]
                 (let [message (str "line 0 - logged by 'log-multi-lines'\n"
                                    "line 1 - logged by 'log-multi-lines'\n"
                                    "line 2 - logged by 'log-multi-lines'")]
                   (plugin/log plugin message)))}
          }}))

(plugin/run plugin)
