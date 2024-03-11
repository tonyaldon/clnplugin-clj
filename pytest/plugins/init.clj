(ns init
  (:require [clnplugin-clj :as plugin]))

(def x (atom nil))

(def plugin
  (atom {:options {:foo {:type "string"
                         :description "foo-description"}
                   :bar {:type "string"
                         :description "bar-description"
                         :default "bar-default"}}
         :rpcmethods
         {:get-x-set-at-init {:fn (fn [plugin params] {:x @x})}
          :get-plugin-options-values
          {:fn (fn [params plugin]
                 {:foo (get-in @plugin [:options :foo :value])
                  :bar (get-in @plugin [:options :bar :value])})}}
         :init-fn (fn [req plugin] (swap! x (constantly 1)))}))

(plugin/run plugin)
