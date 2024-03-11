(ns foo
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:foo {:fn (fn [params plugin] {:bar "baz"})}}}))

(plugin/run plugin)
