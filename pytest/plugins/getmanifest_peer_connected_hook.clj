(ns getmanifest-peer-connected-hook
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods {:peer_connected {:fn (fn [params req plugin])}}}))

(plugin/run plugin)
