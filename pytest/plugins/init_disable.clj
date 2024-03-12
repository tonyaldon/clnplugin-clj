(ns init-disable
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:init-fn (fn [req plugin] {:disable "disabled by user"})}))

(plugin/run plugin)
