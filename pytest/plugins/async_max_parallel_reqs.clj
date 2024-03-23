(ns async-sync
  (:require [clnplugin-clj :as plugin]))

(def counter (atom 0))

(def plugin
  (atom {:max-parallel-reqs 2
         :rpcmethods
         {:sleep-and-update-counter
          {:fn (fn [params req plugin]
                 (Thread/sleep 1000)
                 {:counter (swap! counter inc)})}}}))

(plugin/run plugin)
