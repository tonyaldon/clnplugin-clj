(ns errors
  (:require [clnplugin-clj :as plugin]))

(def plugin
  (atom {:rpcmethods
         {:custom-error
          {:fn (fn [params plugin]
                 (throw
                  (let [msg "custom-error"]
                    (ex-info msg {:error
                                  {:code -100 :message msg}}))))}
          :execution-error {:fn (fn [params plugin] (/ 1 0))}
          :not-a-function {:fn [:a-vector "is not a function"]}
          :non-json-writable-in-result
          {:fn (fn [params plugin]
                 ;; `swap!` returns new value of plugin
                 ;; which contains :_out and :_resps (non
                 ;; json writable) and as it is the last
                 ;; expression in function body, clnplugin-clj
                 ;; will try to use it as :result value in
                 ;; the json response to lightningd.
                 ;; Fortunately, we catch that Exception
                 ;; and return an json rpc error to lightningd.
                 (swap! plugin assoc :bar "baz"))}
          :non-json-writable-in-error
          {:fn (fn [params plugin]
                 (throw
                  ;; atom as :message value is not json writable
                  (ex-info "non-json-writable-in-error"
                           {:error
                            {:code -100 :message (atom nil)}})))}
          }}))


(plugin/run plugin)
