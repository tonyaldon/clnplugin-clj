(ns rpcmethods
  (:require [clnplugin-clj :as plugin]))

(defn foo-2 [params plugin] {:bar-2 "baz-2"})
(defn foo-3 [params plugin] {:bar-3 "baz-3"})

(def plugin
  (atom {:rpcmethods
         {:foo-0 {:fn (fn [params plugin] {:bar "baz"})}
          :foo-1 {:fn (fn [params plugin] {:bar-1 (:baz-1 params)})}
          :foo-2 {:fn foo-2}
          :foo-3 {:fn 'foo-3}
          :foo-4 {:fn (fn [params plugin]
                        (swap! plugin assoc :bar-4 "baz-4")
                        {})}
          :foo-5 {:fn (fn [params plugin]
                        {:bar-5 (loop [bar-4 nil]
                                  (or bar-4 (recur (:bar-4 @plugin))))})}
          }}))


(plugin/run plugin)
