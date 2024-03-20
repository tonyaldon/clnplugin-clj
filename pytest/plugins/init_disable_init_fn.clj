(ns init-disable-init-fn
  (:require [clnplugin-clj :as plugin]))

(def plugin (atom {:init-fn (fn [req plugin]
                              (throw (ex-info "disabled by user" {})))}))

(plugin/run plugin)
