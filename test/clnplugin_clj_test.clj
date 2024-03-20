(ns clnplugin-clj-test
  "Test clnplugin-clj library."
  (:require [clojure.test :refer :all])
  (:require [clnplugin-clj :as plugin])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as str]))

(use-fixtures :once (fn [f] (f) (shutdown-agents)))

(deftest gm-option-test
  ;; defaults when option map argument is `nil`
  (is (= (plugin/gm-option [:foo nil])
         {:name "foo"
          :type "string"
          :description ""}))
  ;; types
  (is (= (plugin/gm-option [:foo {:type "string"
                                  :description "foo-description"}])
         {:name "foo"
          :type "string"
          :description "foo-description"}))
  ;; description
  (is (= (plugin/gm-option [:foo {:type "string"
                                  :description "foo-description"}])
         {:name "foo"
          :type "string"
          :description "foo-description"}))
  ;; default
  (is (= (plugin/gm-option [:foo {:type "string"
                                  :description "foo-description"
                                  :default "foo bar baz"}])
         {:name "foo"
          :type "string"
          :description "foo-description"
          :default "foo bar baz"}))
  ;; multi
  (is (= (plugin/gm-option [:foo {:type "string"
                                  :description "foo-description"
                                  :multi true}])
         {:name "foo"
          :type "string"
          :description "foo-description"
          :multi true}))
  (is (= (plugin/gm-option [:foo {:type "int"
                                  :description "foo-description"
                                  :multi true}])
         {:name "foo"
          :type "int"
          :description "foo-description"
          :multi true}))
  ;; dynamic
  (is (= (plugin/gm-option [:foo {:type "string"
                                  :description "foo-description"
                                  :dynamic true}])
         {:name "foo"
          :type "string"
          :description "foo-description"
          :dynamic true}))
  ;; deprecated
  (is (= (plugin/gm-option [:foo {:type "string"
                                  :description "foo-description"
                                  :deprecated true}])
         {:name "foo"
          :type "string"
          :description "foo-description"
          :deprecated true})))

(deftest gm-options-test
  (is (= (plugin/gm-options {}) []))
  (is (= (plugin/gm-options {:foo {:type "string"
                                   :description "foo-description"}})
         [{:name "foo"
           :type "string"
           :description "foo-description"}]))
  (is (= (plugin/gm-options {:foo-1 nil})
         [{:name "foo-1"
           :type "string"
           :description ""}]))
  (is (= (plugin/gm-options {:foo-1 nil
                             :foo-2 {:type "string"
                                     :description "foo-2-description"}
                             :foo-3 {:type "int"
                                     :description "foo-3-description"
                                     :default 1}
                             :foo-4 {:type "string"
                                     :description "foo-4-description"
                                     :multi true}})
         [{:name "foo-1"
           :type "string"
           :description ""}
          {:name "foo-2"
           :type "string"
           :description "foo-2-description"}
          {:name "foo-3"
           :type "int"
           :description "foo-3-description"
           :default 1}
          {:name "foo-4"
           :type "string"
           :description "foo-4-description"
           :multi true}])))

(deftest gm-rpcmethods-test
  (is (= (plugin/gm-rpcmethods {}) []))
  (is (= (plugin/gm-rpcmethods
          {:foo {:usage "usage"
                 :description "description"
                 :fn (fn [params plugin])}})
         [{:name "foo" :usage "usage" :description "description"}]))
  (is (= (plugin/gm-rpcmethods
          {:foo-1 {:fn (fn [params plugin])}
           :foo-2 {:usage "usage-2"
                   :fn (fn [params plugin])}
           :foo-3 {:description "description-3"
                   :fn (fn [params plugin])}
           :foo-4 {:usage "usage-4"
                   :description "description-4"
                   :fn (fn [params plugin])}})
         [{:name "foo-1" :usage "" :description ""}
          {:name "foo-2" :usage "usage-2" :description ""}
          {:name "foo-3" :usage "" :description "description-3"}
          {:name "foo-4" :usage "usage-4" :description "description-4"}]))
  ;; :fn is not defined
  (is (thrown-with-msg?
       Throwable
       #":fn is not defined for ':foo' RPC method"
       (plugin/gm-rpcmethods {:foo {}})))
  ;; error because :fn is not a function
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' RPC method definition.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (plugin/gm-rpcmethods {:foo {:fn [:a-vector "is not a function"]}})))
  ;; error because :fn is not a function (we don't allow symbol as value of :fn)
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' RPC method definition.  :fn must be a function not 'some-symbol' which is an instance of 'class clojure.lang.Symbol'"
       (plugin/gm-rpcmethods {:foo {:fn 'some-symbol}}))))

(deftest gm-resp-test
  ;; defaults
  (is (= (let [plugin (atom {:options {}
                             :rpcmethods {}
                             :dynamic true})
               req {:id 16}]
           (plugin/gm-resp req plugin))
         {:jsonrpc "2.0"
          :id 16
          :result {:options []
                   :rpcmethods []
                   :dynamic true}}))
  ;; plugin's keys not retained
  (is (= (let [plugin (atom {:options {}
                             :rpcmethods {}
                             :dynamic false
                             :key-not-retained "not retained"})
               req {:id 16}]
           (plugin/gm-resp req plugin))
         {:jsonrpc "2.0"
          :id 16
          :result {:options []
                   :rpcmethods []
                   :dynamic false}}))
  ;; options and rpcmethods not empty
  (is (= (let [plugin
               (atom {:options {:foo-1 nil
                                :foo-2 {:type "string"
                                        :description "foo-2-description"}
                                :foo-3 {:type "int"
                                        :description "foo-3-description"
                                        :default 1}
                                :foo-4 {:type "string"
                                        :description "foo-4-description"
                                        :multi true}}
                      :rpcmethods {:foo-1 {:fn (fn [param plugin])}
                                   :foo-2 {:usage "usage-2"
                                           :fn (fn [param plugin])}
                                   :foo-3 {:description "description-3"
                                           :fn (fn [param plugin])}
                                   :foo-4 {:usage "usage-4"
                                           :description "description-4"
                                           :fn (fn [param plugin])}}
                      :dynamic true})
               req {:id 16}]
           (plugin/gm-resp req plugin))
         {:jsonrpc "2.0"
          :id 16
          :result {:options [{:name "foo-1"
                              :type "string"
                              :description ""}
                             {:name "foo-2"
                              :type "string"
                              :description "foo-2-description"}
                             {:name "foo-3"
                              :type "int"
                              :description "foo-3-description"
                              :default 1}
                             {:name "foo-4"
                              :type "string"
                              :description "foo-4-description"
                              :multi true}]
                   :rpcmethods [{:name "foo-1" :usage "" :description ""}
                                {:name "foo-2" :usage "usage-2" :description ""}
                                {:name "foo-3" :usage "" :description "description-3"}
                                {:name "foo-4" :usage "usage-4" :description "description-4"}]
                   :dynamic true}
          }))
  ;; error because :fn is not a function for foo rpcmethod
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' RPC method definition.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (let [plugin (atom {:options {}
                           :rpcmethods {:foo {:fn [:a-vector "is not a function"]}}
                           :dynamic true})
             req {:id 16}]
         (plugin/gm-resp req plugin)))))

(deftest set-defaults!-test
  (is (= (let [plugin (atom nil)]
           (plugin/set-defaults! plugin)
           @plugin)
         {:options {}
          :rpcmethods {}
          :dynamic true}))
  (is (= (let [plugin (atom {:options {:opt1 'opt1}})]
           (plugin/set-defaults! plugin)
           @plugin)
         {:options {:opt1 'opt1}
          :rpcmethods {}
          :dynamic true}))
  (is (= (let [plugin (atom {:rpcmethods {:foo 'foo}})]
           (plugin/set-defaults! plugin)
           @plugin)
         {:options {}
          :rpcmethods {:foo 'foo}
          :dynamic true}))
  (is (= (let [plugin (atom {:options {:opt1 'opt1}
                             :rpcmethods {:foo 'foo}})]
           (plugin/set-defaults! plugin)
           @plugin)
         {:options {:opt1 'opt1}
          :rpcmethods {:foo 'foo}
          :dynamic true}))
  (is (= (let [plugin (atom {:dynamic false})]
           (plugin/set-defaults! plugin)
           @plugin)
         {:options {}
          :rpcmethods {}
          :dynamic false}))
  (is (= (let [plugin (atom {:options {:opt1 'opt1}
                             :rpcmethods {:foo 'foo}
                             :dynamic false})]
           (plugin/set-defaults! plugin)
           @plugin)
         {:options {:opt1 'opt1}
          :rpcmethods {:foo 'foo}
          :dynamic false})))

(deftest add-request!-test
  (is (= (let [plugin (atom nil)
               req {:method "getmanifest"
                    :params {:allow-deprecated-apis false}}]
           (plugin/add-request! req plugin)
           @plugin)
         {:getmanifest {:allow-deprecated-apis false}}))
  (is (= (let [plugin (atom nil)
               req {:method "init"
                    :params
                    {:options {:bar "BAR"}
                     :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                     :rpc-file "lightning-rpc"}}}]
           (plugin/add-request! req plugin)
           @plugin)
         {:init {:options {:bar "BAR"}
                 :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                 :rpc-file "lightning-rpc"}}})))

(deftest process-getmanifest!-test
  (let [plugin (atom {:options {:opt 'opt}
                      :rpcmethods {:foo {:fn (fn [params plugin])}}
                      :dynamic true
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "getmanifest" :params {:allow-deprecated-apis false}}]
    (plugin/process-getmanifest! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :id 0
            :result {:options [{:name "opt" :type "string" :description ""}]
                     :rpcmethods [{:name "foo" :usage "" :description ""}]
                     :dynamic true}}))
    (is (=
         (:getmanifest @plugin)
         {:allow-deprecated-apis false}))))

(deftest get-option-test
  (let [plugin (atom {:options
                      {:foo-1 nil
                       :foo-2 {:type "string"
                               :description "foo-description"}
                       :foo-3 {:default 1}
                       :foo-4 {:value 2}
                       :foo-5 {:default 1
                               :value 2}}})]
    (is (= (plugin/get-option plugin :foo-1) nil))
    (is (= (plugin/get-option plugin :foo-2) nil))
    (is (= (plugin/get-option plugin :foo-3) 1))
    (is (= (plugin/get-option plugin :foo-4) 2))
    (is (= (plugin/get-option plugin :foo-5) 2))))

(deftest convert-opt-value-test
  (is (= (plugin/convert-opt-value 12 nil) 12))
  (is (= (plugin/convert-opt-value 12 "int") 12))
  (is (= (plugin/convert-opt-value -12 nil) -12))
  (is (= (plugin/convert-opt-value -12 "int") -12))
  (is (= (plugin/convert-opt-value true nil) true))
  (is (= (plugin/convert-opt-value true "bool") true))
  (is (= (plugin/convert-opt-value false nil) false))
  (is (= (plugin/convert-opt-value false "bool") false))
  (is (= (plugin/convert-opt-value "foo" nil) "foo"))
  (is (= (plugin/convert-opt-value "foo" "string") "foo"))
  (is (= (plugin/convert-opt-value "12" "string") "12"))
  (is (= (plugin/convert-opt-value "12" "int") 12))
  (is (= (plugin/convert-opt-value "-12" "int") -12))
  (is (= (plugin/convert-opt-value "true" "bool") true))
  (is (= (plugin/convert-opt-value "false" "bool") false))
  ;; when `setconfig` has just the config argument set,
  ;; this indicate to turn on the flag
  ;; There's no way to turn off a flag dynamically as of CLN 24.02
  (is (= (plugin/convert-opt-value nil "flag") true))
  (is (= (plugin/convert-opt-value "true" "flag") true))
  (is (= (plugin/convert-opt-value "false" "flag") false)))

(deftest set-option!-test
  (try
    (let [plugin (atom {:options {}})]
      (plugin/set-option! [:foo "foo-value"] plugin :at-init))
    (catch Exception e
      (is (= (ex-data e)
             {:disable "Cannot set ':foo' option which has not been declared to lightningd"}))))
  (try
    (let [plugin (atom {:options {}})]
      (plugin/set-option! [:foo "foo-value"] plugin))
    (catch Exception e
      (is (= (ex-data e)
             {:error {:code -32602
                      :message "Cannot set ':foo' option which has not been declared to lightningd"}}))))
  (is (= (let [plugin (atom {:options {:foo nil}})]
           (plugin/set-option! [:foo "foo-value"] plugin :at-init)
           @plugin)
         {:options {:foo {:value "foo-value"}}}))
  (is (= (let [plugin (atom {:options {:foo {:dynamic true}}})]
           (plugin/set-option! [:foo "foo-value"] plugin)
           @plugin)
         {:options {:foo {:dynamic true
                          :value "foo-value"}}}))
  (try
    (let [plugin (atom {:options {:foo nil}})]
      (plugin/set-option! [:foo "foo-value"] plugin))
    (catch Exception e
      (is (ex-data e)
          {:error {:code -32602
                   :message "Cannot set ':foo' option which is not dynamic.  Add ':dynamic true' to its declaration."}})))
  (is (= (let [plugin (atom {:options {:foo nil
                                       :bar {:default "bar-default"}}})]
           (plugin/set-option! [:bar "bar-value"] plugin :at-init)
           @plugin)
         {:options {:foo nil
                    :bar {:default "bar-default"
                          :value "bar-value"}}}))
  (is (= (let [plugin (atom {:options {:foo nil
                                       :bar {:default "bar-default"
                                             :dynamic true}}})]
           (plugin/set-option! [:bar "bar-value"] plugin)
           @plugin)
         {:options {:foo nil
                    :bar {:default "bar-default"
                          :dynamic true
                          :value "bar-value"}}}))
  ;; :check-opt throws an error so the plugin will be disable at init round
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:check-opt
                          (fn [value plugin]
                            (throw (ex-info "Wrong option 'foo'" {})))}}})]
      (plugin/set-option! [:foo "foo-value"] plugin :at-init))
    (catch Exception e
      (is (= (ex-data e) {:disable "Wrong option 'foo'"}))))
  ;; :check-opt throws an error so the option won't be set by lightningd (dynamic option)
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:dynamic true
                          :check-opt
                          (fn [value plugin]
                            (throw (ex-info "Wrong option 'foo'" {})))}}})]
      (plugin/set-option! [:foo "foo-value"] plugin))
    (catch Exception e
      (is (= (ex-data e)
             {:error {:code -32602 :message "Wrong option 'foo'"}}))))
  ;; :check-opt is not a function
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:check-opt [:a-vector "is not a function"]}}})]
      (plugin/set-option! [:foo "foo-value"] plugin :at-init))
    (catch Exception e
      (is (= (ex-data e)
             {:disable ":check-opt of ':foo' option must be a function not '[:a-vector \"is not a function\"]' which is an instance of 'class clojure.lang.PersistentVector'"}))))
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:check-opt 'some-symbol}}})]
      (plugin/set-option! [:foo "foo-value"] plugin :at-init))
    (catch Exception e
      (is (= (ex-data e)
             {:disable ":check-opt of ':foo' option must be a function not 'some-symbol' which is an instance of 'class clojure.lang.Symbol'"}))))
  ;; :check-opt is not a function (dynamic option)
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:dynamic true
                          :check-opt [:a-vector "is not a function"]}}})]
      (plugin/set-option! [:foo "foo-value"] plugin))
    (catch Exception e
      (is (= (ex-data e)
             {:error {:code -32602
                      :message ":check-opt of ':foo' option must be a function not '[:a-vector \"is not a function\"]' which is an instance of 'class clojure.lang.PersistentVector'"}}))))
  ;; :check-opt throws an error at execution time
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:check-opt (fn [value plugin] (/ 1 0))}}})]
      (plugin/set-option! [:foo "foo-value"] plugin :at-init))
    (catch Exception e
      (is (re-find
           #"(?s):check-opt of ':foo' option thrown the following exception when called with 'foo-value' value:.*#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (:disable (ex-data e))))))
  ;; :check-opt throws an error at execution time (dynamic option)
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:dynamic true
                          :check-opt (fn [value plugin] (/ 1 0))}}})]
      (plugin/set-option! [:foo "foo-value"] plugin))
    (catch Exception e
      (is (re-find
           #"(?s):check-opt of ':foo' option thrown the following exception when called with 'foo-value' value:.*#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (get-in (ex-data e) [:error :message])))))
  ;; side effect with :check-opt while setting foo option
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt
                        (fn [value plugin]
                          (swap! plugin assoc :bar "baz"))}}})]
    (plugin/set-option! [:foo "foo-value"] plugin :at-init)
    (is (= (:bar @plugin) "baz"))
    (is (= (get-in @plugin [:options :foo :value]) "foo-value")))
  (let [plugin (atom {:options
                      {:foo
                       {:dynamic true
                        :check-opt
                        (fn [value plugin]
                          (swap! plugin assoc :bar "baz"))}}})]
    (plugin/set-option! [:foo "foo-value"] plugin)
    (is (= (:bar @plugin) "baz"))
    (is (= (get-in @plugin [:options :foo :value]) "foo-value"))))

(deftest set-options-at-init!-test
  (is (= (let [plugin (atom {:options {:foo nil
                                       :bar {:default "bar-default"}
                                       :baz {:dynamic true}}})]
           (plugin/set-options-at-init! {} plugin)
           @plugin)
         {:options {:foo nil
                    :bar {:default "bar-default"}
                    :baz {:dynamic true}}}))
  (is (= (let [plugin (atom {:options {:foo nil
                                       :bar {:default "bar-default"}
                                       :baz {:dynamic true}}})]
           (plugin/set-options-at-init! {:foo "foo-value"} plugin)
           @plugin)
         {:options {:foo {:value "foo-value"}
                    :bar {:default "bar-default"}
                    :baz {:dynamic true}}}))
  (is (= (let [plugin (atom {:options {:foo nil
                                       :bar {:default "bar-default"}
                                       :baz {:dynamic true}}})]
           (plugin/set-options-at-init! {:foo "foo-value"
                                         :bar "bar-value"
                                         :baz "baz-value"} plugin)
           @plugin)
         {:options {:foo {:value "foo-value"}
                    :bar {:default "bar-default"
                          :value "bar-value"}
                    :baz {:dynamic true
                          :value "baz-value"}}}))
  (try
    (let [plugin (atom {:options {:foo
                                  {:check-opt
                                   (fn [value plugin]
                                     (throw (ex-info "Wrong option 'foo'" {})))}
                                  :bar {:default "bar-default"}}})]
      (plugin/set-options-at-init! {:foo "foo-value" :bar "bar-value"} plugin))
    (catch clojure.lang.ExceptionInfo e
      (is (= (ex-data e) {:disable "Wrong option 'foo'"})))))

(deftest process-init!-test
  (let [plugin (atom {:options {} :rpcmethods {} :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :id 0 :result {}}))
    (is (= (dissoc @plugin :_out)
           {:options {}
            :rpcmethods {}
            :dynamic true
            :getmanifest {:allow-deprecated-apis false}
            :init {:options {}
                   :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                   :rpc-file "lightning-rpc"}}
            :socket-file "/tmp/l1-regtest/regtest/lightning-rpc"})))
  ;; :init-fn function is ok
  (let [plugin (atom {:options {:foo nil
                                :bar {:default "bar-default"}
                                :baz {:dynamic true}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin]
                                 (swap! plugin assoc-in [:set-by-init-fn] "init-fn"))
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"
                                :bar "bar-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :id 0 :result {}}))
    (is (= (dissoc @plugin :init-fn :_out)
           {:options {:foo {:value "foo-value"}
                      :bar {:default "bar-default"
                            :value "bar-value"}
                      :baz {:dynamic true}}
            :rpcmethods {}
            :dynamic true
            :getmanifest {:allow-deprecated-apis false}
            :init {:options {:foo "foo-value"
                             :bar "bar-value"}
                   :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                   :rpc-file "lightning-rpc"}}
            :socket-file "/tmp/l1-regtest/regtest/lightning-rpc"
            :set-by-init-fn "init-fn"})))
  ;; disable plugin because of :check-opt in plugin options
  ;;
  ;; :check-opt disable the plugin
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt
                        (fn [value plugin]
                          (throw (ex-info "Wrong option 'foo'" {})))}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :id 0
            :result {:disable "Wrong option 'foo'"}})))
  ;; :check-opt is called for each option before we try to run
  ;; :init-fn.  So, a wrong option will disable the plugin before
  ;; we try to run :init-fn
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt
                        (fn [value plugin]
                          (throw (ex-info "Wrong option 'foo'" {})))}}
                      :rpcmethods {}
                      :dynamic true
                      :init-fn (fn [req plugin] (/ 1 0))
                      :getmanifest {:allow-deprecated-apis false}
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :id 0
            :result {:disable "Wrong option 'foo'"}})))

  ;; :check-opt is not a function
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt [:a-vector "is not a function"]}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :id 0
            :result {:disable ":check-opt of ':foo' option must be a function not '[:a-vector \"is not a function\"]' which is an instance of 'class clojure.lang.PersistentVector'"}})))

  ;; :check-opt throws an error at execution time
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt (fn [value plugin] (/ 1 0))}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (re-find
         #"(?s):check-opt of ':foo' option thrown the following exception when called with 'foo-value' value:.*#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
         (get-in (json/read-str (str (:_out @plugin)) :key-fn keyword)
                 [:result :disable]))))
  ;; disable plugin because of :init-fn plugin key
  ;;
  ;; :init-fn not a function
  (let [plugin (atom {:options {}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn 'not-a-function
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :id 0
            :result {:disable ":init-fn must be a function not 'not-a-function' which is an instance of 'class clojure.lang.Symbol'"}})))
  ;; :init-fn throws an error
  (let [plugin (atom {:options {}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin] (/ 1 0))
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (let [resp (json/read-str (str (:_out @plugin)) :key-fn keyword)]
      (is (re-find
           #"(?s)#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (get-in resp [:result :disable])))))
  ;; :init-fn disable the plugin
  (let [plugin (atom {:options {}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin]
                                 (throw (ex-info "disabled by user" {})))
                      :_out (new java.io.StringWriter)})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (plugin/process-init! req plugin)
    (let [resp (json/read-str (str (:_out @plugin)) :key-fn keyword)]
      (is (= (-> resp :result :disable str/split-lines first)
             "disabled by user")))))

(deftest setconfig!-test
  ;; Note that lightningd send `setconfig` requests
  ;; to the plugin with `val` in `params` being a string
  ;; even if the option is defined by the plugin as being
  ;; of type "int" or "bool".  If the option is of type
  ;; "flag", `val` is not present in the request and this
  ;; means that we turn on the flag option.  There is no
  ;; way to turn off a flag option dynamically as of CLN 24.02.
  (let [plugin (atom {:options {:foo {:dynamic true}}})
        params {:config "foo", :val "foo-value"}
        resp (plugin/setconfig! params plugin)]
    (is (= resp {}))
    (is (= @plugin {:options {:foo {:value "foo-value"
                                    :dynamic true}}})))
  (let [plugin (atom {:options {:foo {:type "int"
                                      :dynamic true}}})
        params-0 {:config "foo", :val "12"}
        params-1 {:config "foo", :val "-12"}]
    (is (= (plugin/setconfig! params-0 plugin) {}))
    (is (= @plugin {:options {:foo {:value 12
                                    :type "int"
                                    :dynamic true}}}))
    (is (= (plugin/setconfig! params-1 plugin) {}))
    (is (= @plugin {:options {:foo {:value -12
                                    :type "int"
                                    :dynamic true}}})))
  (let [plugin (atom {:options {:foo {:type "bool"
                                      :dynamic true}}})
        params-0 {:config "foo", :val "true"}
        params-1 {:config "foo", :val "false"}]
    (is (= (plugin/setconfig! params-0 plugin) {}))
    (is (= @plugin {:options {:foo {:value true
                                    :type "bool"
                                    :dynamic true}}}))
    (is (= (plugin/setconfig! params-1 plugin) {}))
    (is (= @plugin {:options {:foo {:value false
                                    :type "bool"
                                    :dynamic true}}})))
  (let [plugin (atom {:options {:foo {:type "flag"
                                      :dynamic true}}})
        params {:config "foo"}]
    (is (= (plugin/setconfig! params plugin) {}))
    (is (= @plugin {:options {:foo {:value true
                                    :type "flag"
                                    :dynamic true}}})))
  (try
    (let [plugin (atom {:options {:foo nil}})
          params {:config "foo", :val "foo-value"}]
      (plugin/setconfig! params plugin))
    (catch Exception e
      (is (= (ex-data e)
             {:error {:code -32602
                      :message "Cannot set ':foo' option which is not dynamic.  Add ':dynamic true' to its declaration."}}))))
  ;; :check-opt
  (let [plugin (atom {:options
                      {:foo
                       {:dynamic true
                        :check-opt (fn [value plugin]
                                     (when-not (and (number? value) (pos? value))
                                       (throw (ex-info "'foo' option must be positive" {}))))}}})
        params-ok {:config "foo", :val 12}
        params-wrong {:config "foo", :val -1}]
    (is (= (plugin/setconfig! params-ok plugin) {}))
    (is (= (get-in @plugin [:options :foo :value]) 12))
    (try
      (plugin/setconfig! params-wrong plugin)
      (catch Exception e
        (is (= (ex-data e)
               {:error {:code -32602 :message "'foo' option must be positive"}})))))
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:dynamic true
                          :check-opt [:a-vector "is not a function"]}}})
          params {:config "foo", :val "foo-value"}]
      (plugin/setconfig! params plugin))
    (catch Exception e
      (is (= (ex-data e)
             {:error {:code -32602
                      :message ":check-opt of ':foo' option must be a function not '[:a-vector \"is not a function\"]' which is an instance of 'class clojure.lang.PersistentVector'"}}))))

  (try
    (let [plugin (atom {:options
                        {:foo
                         {:dynamic true
                          :check-opt (fn [value plugin] (/ 1 0))}}})
          params {:config "foo", :val "foo-value"}]
      (plugin/setconfig! params plugin))
    (catch Exception e
      ;; (prn e)
      (is (re-find
           #"(?s):check-opt of ':foo' option thrown the following exception when called with 'foo-value' value:.*#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (get-in (ex-data e) [:error :message]))))))

(deftest add-rpcmethod!-test
  (let [plugin (atom {:rpcmethods {}})
        foo-fn (fn [params plugin] {:bar "baz"})]
    (plugin/add-rpcmethod! :foo foo-fn plugin)
    (is (= (get-in @plugin [:rpcmethods :foo :fn])
           foo-fn))
    (plugin/add-rpcmethod! :setconfig plugin/setconfig! plugin)
    (is (= (get-in @plugin [:rpcmethods :setconfig :fn])
           plugin/setconfig!))))


(deftest notif-test
  (let [method "foo" params {:bar "baz"}]
    (is (= (plugin/notif method params)
           {:jsonrpc "2.0" :method method :params params}))))

(deftest write-test
  (let [out (new java.io.StringWriter)
        resp-0 {:jsonrpc "2.0" :id "id-0" :result {:foo "bar"}}
        resp-1 {:jsonrpc "2.0" :id "id-1" :result ""}
        resp-2 {:jsonrpc "2.0" :id "id-2" :result nil}
        resp-3 {:jsonrpc "2.0" :id "id-3" :error {:code -32600 :message "Something wrong happened"}}
        resp-4 {:jsonrpc "2.0" :method "log" :params {:level "debug" :message "Some message"}}]
    (plugin/write nil [['req-0 resp-0]] out)
    (plugin/write nil [['req-0 resp-0] ['req-1 resp-1] ['req-2 resp-2] ['req-3 resp-3]] out)
    (plugin/write nil [[nil resp-4]] out)
    (let [outs (str/split (str out) #"\n\n")
          resps (map #(json/read-str % :key-fn keyword) outs)]
      (is (= resps (list resp-0
                         resp-0 resp-1 resp-2 resp-3
                         resp-4)))))
  ;; Handle non json writable in :result, :error or :params
  ;; in responses to requests or in notifications we try to send to lightningd
  ;;
  ;; resp with :result
  (let [out (new java.io.StringWriter)
        req {:jsonrpc "2.0" :id "some-id" :method "foo" :params {:bar "baz"}}
        resp {:jsonrpc "2.0" :id "some-id" :result (atom nil)}]
    (plugin/write nil [[req resp]] out)
    (let [outs (str/split (str out) #"\n\n")
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          err (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      ;; error
      (is (re-find
           #"Error while processing.*:method.*foo"
           (get-in err [:error :message])))
      (is (re-find
           #"(?s)#error.*Don't know how to write JSON of class clojure.lang.Atom"
           (get-in err [:error :exception])))
      (is (= (get-in err [:error :request])
             req))
      (is (re-find
           #"#object\[clojure.lang.Atom .*\]"
           (get-in err [:error :response :result])))
      ;; logs
      (is (some #(re-find #"Error while processing.*:method.*foo"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #"#error" (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #".*Don't know how to write JSON of class clojure.lang.Atom"
                          (get-in % [:params :message]))
                resp-and-logs))))
  ;; resp with :error
  (let [out (new java.io.StringWriter)
        req {:jsonrpc "2.0" :id "some-id" :method "foo" :params {:bar "baz"}}
        resp {:jsonrpc "2.0" :id "some-id" :error (atom nil)}]
    (plugin/write nil [[req resp]] out)
    (let [outs (str/split (str out) #"\n\n")
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          err (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      ;; error
      (is (re-find
           #"Error while processing.*:method.*foo"
           (get-in err [:error :message])))
      (is (re-find
           #"(?s)#error.*Don't know how to write JSON of class clojure.lang.Atom"
           (get-in err [:error :exception])))
      (is (= (get-in err [:error :request])
             req))
      (is (re-find
           #"#object\[clojure.lang.Atom .*\]"
           (get-in err [:error :response :error])))
      ;; logs
      (is (some #(re-find #"Error while processing.*:method.*foo"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #"#error" (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #".*Don't know how to write JSON of class clojure.lang.Atom"
                          (get-in % [:params :message]))
                resp-and-logs))))
  ;; notifications
  (let [out (new java.io.StringWriter)
        notif {:jsonrpc "2.0" :method "some-notif" :params (atom nil)}]
    (plugin/write nil [[nil notif]] out)
    (let [outs (str/split (str out) #"\n\n")
          logs (map #(json/read-str % :key-fn keyword) outs)]
      ;; logs
      (is (some #(re-find #"Error while sending notification.*:method.*some-notif"
                          (get-in % [:params :message]))
                logs))
      (is (some #(re-find #"#error" (get-in % [:params :message]))
                logs))
      (is (some #(re-find #".*Don't know how to write JSON of class clojure.lang.Atom"
                          (get-in % [:params :message]))
                logs)))))

(deftest log-test
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "foo"]
    (plugin/log plugin message)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "log"
            :params {:level "info" :message "foo"}})))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "bar"
        level "debug"]
    (plugin/log plugin message level)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "log"
            :params {:level "debug" :message "bar"}})))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "foo-1\nfoo-2\nfoo-3\n"]
    (plugin/log plugin message)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (let [srdr (java.io.StringReader. (str (:_out @plugin)))
                 pbr (java.io.PushbackReader. srdr 64)]
             (for [_ (range 3)]
               (json/read pbr :key-fn keyword)))
           '({:jsonrpc "2.0", :method "log", :params {:level "info", :message "foo-1"}}
             {:jsonrpc "2.0", :method "log", :params {:level "info", :message "foo-2"}}
             {:jsonrpc "2.0", :method "log", :params {:level "info", :message "foo-3"}})))))

(deftest process-test
  (let [foo-2 (fn [params plugin] {:bar-2 "baz-2"})
        plugin (atom {:rpcmethods
                      {:foo-0 {:fn (fn [params plugin] {:bar "baz"})}
                       :foo-1 {:fn (fn [params plugin] {:bar-1 (:baz-1 params)})}
                       :foo-2 {:fn foo-2}
                       :foo-3 {:fn (fn [params plugin]
                                     (swap! plugin assoc :bar-3 "baz-3")
                                     {})}
                       :foo-4 {:fn (fn [params plugin]
                                     {:bar-4 (loop [bar-3 nil]
                                               (or bar-3 (recur (:bar-3 @plugin))))})}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req-0 {:jsonrpc "2.0" :id "id-0" :method "foo-0" :params {}}
        req-1 {:jsonrpc "2.0" :id "id-1" :method "foo-1" :params {:baz-1 "baz-1"}}
        req-2 {:jsonrpc "2.0" :id "id-2" :method "foo-2" :params {}}
        req-3 {:jsonrpc "2.0" :id "id-3" :method "foo-3" :params {}}
        req-4 {:jsonrpc "2.0" :id "id-4" :method "foo-4" :params {}}]
    (plugin/process req-0 plugin)
    (Thread/sleep 100) ;; because `plugin/process` calls are asynchronous (in go blocks)
    (plugin/process req-1 plugin)
    (Thread/sleep 100)
    (plugin/process req-2 plugin)
    (Thread/sleep 100)
    (plugin/process req-3 plugin)
    (Thread/sleep 100)
    (plugin/process req-4 plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resps (map #(json/read-str % :key-fn keyword) outs)]
      (is (= resps
             '({:jsonrpc "2.0" :id "id-0" :result {:bar "baz"}}
               {:jsonrpc "2.0" :id "id-1" :result {:bar-1 "baz-1"}}
               {:jsonrpc "2.0" :id "id-2" :result {:bar-2 "baz-2"}}
               {:jsonrpc "2.0" :id "id-3" :result {}}
               {:jsonrpc "2.0" :id "id-4" :result {:bar-4 "baz-3"}})))))

  ;; Custom errors raised by user
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params plugin]
                              (throw
                               (let [msg "custom-error"]
                                 (ex-info msg {:error
                                               {:code -100 :message msg}}))))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          resp (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      (is (= resp
             {:jsonrpc "2.0"
              :id "some-id"
              :error
              {:code -100 :message "custom-error"}}))
      (is (some #(re-find #"Error while processing.*method.*custom-error"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #"code.*-100.*message.*custom-error"
                          (get-in % [:params :message]))
                resp-and-logs))))

  ;; missing :code key in the error thrown by :fn
  ;; so it is set to -32603
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params plugin]
                              (throw
                               (let [msg "custom-error"]
                                 (ex-info msg {:error {:message msg}}))))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          resp (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      (is (= resp
             {:jsonrpc "2.0"
              :id "some-id"
              :error
              {:code -32603 :message "custom-error"}}))))
  ;; missing :message key in the error thrown by :fn
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params plugin]
                              (throw
                               (ex-info "custom-error" {:error {:code -100}})))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          resp (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      (is (= (get-in resp [:error :code]) -100))
      (is (re-find #"Error while processing.*method.*custom-error"
                   (get-in resp [:error :message])))))
  ;; empty data in the error thrown by :fn
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params plugin]
                              (throw
                               (ex-info "custom-error" {})))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          resp (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      (is (= (get-in resp [:error :code]) -32603))
      (is (re-find #"Error while processing.*method.*custom-error"
                   (get-in resp [:error :message])))))
  ;; Execution errors
  (let [plugin (atom {:rpcmethods
                      {:execution-error
                       {:fn (fn [params plugin] (/ 1 0))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "execution-error" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          resp (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      (is (= (get-in resp [:error :code]) -32603))
      (is (re-find #"Error while processing.*method.*execution-error"
                   (get-in resp [:error :message])))
      (is (re-find
           #"(?s)#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (get-in resp [:error :exception])))
      (is (some #(re-find #"Error while processing.*method.*execution-error"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(= % {:jsonrpc "2.0"
                       :method "log"
                       :params {:level "debug"
                                :message " :cause \"Divide by zero\""}})
                resp-and-logs))))

  ;; Handle non json writable in :result and :error of responses
  ;; to requests we try to send to lightningd
  ;;
  ;; json rpc result with non json writable
  (let [plugin (atom {:rpcmethods
                      {:non-json-writable-in-result
                       {:fn (fn [params plugin]
                              ;; `swap!` returns new value of plugin
                              ;; which contains :_out and :_resps (non
                              ;; json writable) and as it is the last
                              ;; expression in function body, clnplugin-clj
                              ;; will try to use it as :result value in
                              ;; the json response to lightningd.
                              ;; Fortunately, we catch that Exception
                              ;; and return an json rpc error to lightningd.
                              (swap! plugin assoc :bar "baz"))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "non-json-writable-in-result" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (str/split (str (:_out @plugin)) #"\n\n")
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          err (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      ;; error
      (is (re-find
           #"Error while processing.*:method.*non-json-writable-in-result"
           (get-in err [:error :message])))
      (is (re-find
           #"(?s)#error.*Don't know how to write JSON of class"
           (get-in err [:error :exception])))
      (is (= (get-in err [:error :request])
             req))
      ;; because `swap!` in non-json-writable-in-result method definition returns new value of plugin atom
      (let [result (get-in err [:error :response :result])]
        (is (and (contains? result :rpcmethods)
                 (contains? result :_out)
                 (contains? result :_resps))))
      ;; logs
      (is (some #(re-find #"Error while processing.*:method.*non-json-writable-in-result"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #"#error" (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #".*Don't know how to write JSON of class"
                          (get-in % [:params :message]))
                resp-and-logs))))
  ;; json rpc error with non json writable
  (let [plugin (atom {:rpcmethods
                      {:non-json-writable-in-error
                       {:fn (fn [params plugin]
                              (throw
                               ;; atom as :message value is not json writable
                               (ex-info "non-json-writable-in-error"
                                        {:error
                                         {:code -100 :message (atom nil)}})))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "non-json-writable-in-error" :params {}}]
    (plugin/process req plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (str/split (str (:_out @plugin)) #"\n\n")
          resp-and-logs (map #(json/read-str % :key-fn keyword) outs)
          err (some #(when (= (:id %) "some-id") %) resp-and-logs)]
      ;; error
      (is (re-find
           #"Error while processing.*:method.*non-json-writable-in-error"
           (get-in err [:error :message])))
      (is (re-find
           #"(?s)#error.*Don't know how to write JSON of class"
           (get-in err [:error :exception])))
      (is (= (get-in err [:error :request])
             req))
      (is (re-find
           #"#object\[clojure.lang.Atom .*\]"
           (get-in err [:error :response :error :message])))
      ;; ;; logs
      (is (some #(re-find #"Error while processing.*:method.*non-json-writable-in-error"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #"#error" (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(re-find #".*Don't know how to write JSON of class clojure.lang.Atom"
                          (get-in % [:params :message]))
                resp-and-logs)))))

(deftest read-test
  (is (= (let [req {:jsonrpc "2.0" :id 0 :method "foo" :params {}}
               req-str (str (json/write-str req :escape-slash false) "\n\n")]
           (with-open [in (-> (java.io.StringReader. req-str) clojure.lang.LineNumberingPushbackReader.)]
             (plugin/read in)))
         {:jsonrpc "2.0" :id 0 :method "foo" :params {}}))
  (is (= (let [req-str (str "{\"jsonrpc\":\"2.0\","
                            "\n"
                            "\"id\":0,\"method\":\"foo\",\"params\":{}}"
                            "\n\n")]
           (with-open [in (-> (java.io.StringReader. req-str) clojure.lang.LineNumberingPushbackReader.)]
             (plugin/read in)))
         {:jsonrpc "2.0" :id 0 :method "foo" :params {}}))
  (is (= (let [req-0 {:jsonrpc "2.0" :id 0 :method "foo-0" :params {}}
               req-1 {:jsonrpc "2.0" :id 0 :method "foo-1" :params {}}
               req-0-str (str (json/write-str req-0 :escape-slash false) "\n\n")
               req-1-str (str (json/write-str req-1 :escape-slash false) "\n\n")
               reqs-str (str req-0-str req-1-str)]
           (with-open [in (-> (java.io.StringReader. reqs-str) clojure.lang.LineNumberingPushbackReader.)]
             (plugin/read in)))
         {:jsonrpc "2.0" :id 0 :method "foo-0" :params {}}))
  (is (=
       (let [req-str "foo\n"]
         (with-open [in (-> (java.io.StringReader. req-str) clojure.lang.LineNumberingPushbackReader.)]
           (plugin/read in)))
       nil))
  (try
    (let [req-str "foo\n\n"]
      (with-open [in (-> (java.io.StringReader. req-str) clojure.lang.LineNumberingPushbackReader.)]
        (plugin/read in)))
    (catch clojure.lang.ExceptionInfo e
      (is (= (ex-data e)
             {:error {:code -32700 :message "Invalid token in json input: 'foo'"}})))))
