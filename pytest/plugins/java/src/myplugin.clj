(ns myplugin
  (:require [clnplugin-clj :as plugin])
  (:gen-class))

(def plugin
  (atom {:rpcmethods
         {:foo {:fn (fn [params req plugin] {:bar "baz"})}}}))

(defn -main [& args]
  (plugin/run plugin))
