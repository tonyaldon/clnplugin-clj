(ns init-disable-init-fn-execution-error
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:init-fn (fn [params req plugin] (/ 1 0))}))

(plugin/run plugin)
