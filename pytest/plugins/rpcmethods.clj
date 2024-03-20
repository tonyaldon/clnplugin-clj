(ns rpcmethods
  (:require [clnplugin-clj :as plugin]))

(defn foo-2 [params plugin] {:bar-2 "baz-2"})

(def plugin
  (atom {:rpcmethods
         {:foo-0 {:fn (fn [params plugin] {:bar "baz"})}
          :foo-1 {:fn (fn [params plugin] {:bar-1 (:baz-1 params)})}
          :foo-2 {:fn foo-2}
          :foo-3 {:fn (fn [params plugin]
                        (swap! plugin assoc :bar-3 "baz-3")
                        {})}
          :foo-4 {:fn (fn [params plugin]
                        {:bar-4 (loop [bar-3 nil]
                                  (or bar-3 (recur (:bar-3 @plugin))))})}
          }}))


(plugin/run plugin)
