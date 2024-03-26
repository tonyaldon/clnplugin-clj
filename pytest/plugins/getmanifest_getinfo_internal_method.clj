(ns getmanifest-getinfo-internal-method
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods {:getinfo {:fn (fn [params req plugin])}}}))

(plugin/run plugin)
