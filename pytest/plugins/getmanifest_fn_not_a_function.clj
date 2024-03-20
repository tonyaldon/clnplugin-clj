(ns getmanifest-fn-not-a-function
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods {:foo {:fn [:a-vector "is not a function"]}}}))

(plugin/run plugin)
