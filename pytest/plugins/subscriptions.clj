(ns subscriptions
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:subscriptions
         {:topic-0
          {:fn (fn [params req plugin]
                 (plugin/log
                  (format "Got a topic-0 notification %s from plugin %s"
                          (:payload params) (:origin params))
                  plugin))}
          :topic-1
          {:fn (fn [params req plugin]
                 (plugin/log
                  (format "Got a topic-1 notification %s from plugin %s"
                          (:payload params) (:origin params))
                  plugin))}
          :topic-undeclared-0
          {:fn (fn [params req plugin]
                 (plugin/log
                  (format "Got a topic-undeclared notification %s from plugin %s"
                          (:payload params) (:origin params))
                  plugin))}
          :topic-undeclared-1
          {:fn (fn [params req plugin])}
          :connect
          {:fn (fn [params req plugin]
                 (plugin/log
                  (format "Got a connect notification")
                  plugin))}}}))

(plugin/run plugin)
