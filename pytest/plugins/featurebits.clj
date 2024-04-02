(ns featurebits
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:dynamic false
         :featurebits
         {:init "0200000000000000000000000000000000000000000000000000" ;; 1 << 201
          :node "0800000000000000000000000000000000000000000000000000" ;; 1 << 203
          :invoice "2000000000000000000000000000000000000000000000000000" ;; 1 << 205
          }}))

(plugin/run plugin)
