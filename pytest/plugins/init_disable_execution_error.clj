(ns init-disable-execution-error
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:init-fn (fn [req plugin] (/ 1 0))}))

(plugin/run plugin)
