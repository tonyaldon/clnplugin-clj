(ns errors
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:custom-error
          {:fn (fn [params plugin]
                 (throw
                  (let [msg "custom-error"]
                    (ex-info msg {:error
                                  {:code -100 :message msg}}))))}
          :execution-error
          {:fn (fn [params plugin] (/ 1 0))}}}))

(plugin/run plugin)
