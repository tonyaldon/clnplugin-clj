(ns clnplugin-clj
  "Core Lightning plugin library for Clojure."
  (:require [clojure.data.json :as json]))


(defn run [plugin]
  (let [request (json/read-str (read-line) :key-fn keyword)
        _ (read-line)
        manifest {:jsonrpc "2.0"
                  :id (:id request)
                  :result
                  {:dynamic true
                   :options []
                   :rpcmethods [{:name "foo"
                                 :usage "usage"
                                 :description "description"}]}}]
    (json/write manifest *out* :escape-slash false)
    (flush))

  (let [request (json/read-str (read-line) :key-fn keyword)
        _ (read-line)
        init {:jsonrpc "2.0"
              :id (:id request)
              :result {}}]
    (json/write init *out* :escape-slash false)
    (flush))

  (while true
    (let [request (json/read-str (read-line) :key-fn keyword)
          _ (read-line)
          resp {:jsonrpc "2.0"
                :id (:id request)
                :result {:bar "baz"}}]
      (json/write resp *out* :escape-slash false)
      (flush))))
