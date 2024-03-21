(ns options-dynamic
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:options {:foo-no-check-opt {:dynamic true}
                   :foo-int {:type "int" :dynamic true}
                   :foo-bool {:type "bool" :dynamic true}
                   :foo-flag {:type "flag" :dynamic true}
                   :foo-with-check-opt
                   {:type "int"
                    :dynamic true
                    :check-opt (fn [value plugin]
                                 (when-not (and (number? value) (pos? value))
                                   (throw (ex-info (format "'foo-with-check-opt' option must be positive %s type %s" value (class value)) {}))))}
                   ;; As we start the plugin without setting :foo-wrong-check-opt
                   ;; option and we don't supply a default value for that option,
                   ;; :check-opt function is not called at init and the plugin
                   ;; starts correctly.  But when we'll send a setconfig request
                   ;; to set that option, :check-opt will be called and we want
                   ;; to be sure that the plugin return a correct JSON RPC error
                   ;; lightningd.
                   :foo-wrong-check-opt {:dynamic true
                                         :check-opt [:a-vector "is not a function"]}
                   ;; Same comment as for :foo-wrong-check-opt
                   :foo-error-in-check-opt {:dynamic true
                                            :check-opt (fn [value plugin] (/ 1 0))}}
         :rpcmethods
         {:get-opt-value
          {:fn (fn [params req plugin]
                 (get-in @plugin [:options (keyword (:opt params)) :value]))}}}))

(plugin/run plugin)
