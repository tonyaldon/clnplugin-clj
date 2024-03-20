(ns getmanifest-fn-not-defined
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods {:foo {}}}))

(plugin/run plugin)
