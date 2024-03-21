(ns getmanifest-notifications-do-not-declare-log
  (:require [clnplugin-clj :as plugin]))

(def plugin
  ;; the same for "message" and "progress" notifications
  (atom {:notifications ["log"]}))

(plugin/run plugin)
