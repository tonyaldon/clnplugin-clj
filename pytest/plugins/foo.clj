(ns foo
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:foo {:fn (fn [plugin params] {:bar "baz"})}}}))

(plugin/run plugin)
