(ns getmanifest-fn-symbol-not-a-function
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods {:foo {:fn 'some-symbol}}}))

(plugin/run plugin)
