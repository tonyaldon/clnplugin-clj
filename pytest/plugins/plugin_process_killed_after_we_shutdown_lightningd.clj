(ns plugin-process-killed-when-we-shutdown-lightningd
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {}))

(plugin/run plugin)
