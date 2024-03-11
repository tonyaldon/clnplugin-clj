(ns async
  (:require [clnplugin-clj :as plugin]))

(def counter (atom 0))

(def plugin
  (atom {:rpcmethods
         {:async {:fn (fn [params plugin]
                        (Thread/sleep 1000)
                        {:counter (swap! counter inc)})}}}))

(plugin/run plugin)
