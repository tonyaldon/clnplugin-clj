(ns init-disable-not-a-function
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:init-fn 'not-a-function}))

(plugin/run plugin)
