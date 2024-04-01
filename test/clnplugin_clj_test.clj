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
  (is (= (plugin/gm-option [:foo {:type "int"
                                  :description "foo-description"}])
         {:name "foo"
          :type "int"
          :description "foo-description"}))
  (is (= (plugin/gm-option [:foo {:type "bool"
                                  :description "foo-description"}])
         {:name "foo"
          :type "bool"
          :description "foo-description"}))
  (is (= (plugin/gm-option [:foo {:type "flag"
                                  :description "foo-description"}])
         {:name "foo"
          :type "flag"
          :description "foo-description"}))
  (is (thrown-with-msg?
       Throwable
       #"Wrong type 'not-a-type' for option.*:foo.*.  Authorized types are: string, int, bool, flag."
       (plugin/gm-option [:foo {:type "not-a-type"}])))
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
  (is (= (plugin/gm-option [:foo {:type "int"
                                  :description "foo-description"
                                  :default 1}])
         {:name "foo"
          :type "int"
          :description "foo-description"
          :default 1}))
  (is (thrown-with-msg?
       Throwable
       #"Default value of ':foo' option has the wrong type.  'string' type is expected and default value is '1':"
       (plugin/gm-option [:foo {:type "string"
                                :description "foo-description"
                                :default 1}])))
  (is (thrown-with-msg?
       Throwable
       #"Default value of ':foo' option has the wrong type.  'string' type is expected and default value is '1':"
       (plugin/gm-option [:foo {:description "foo-description"
                                :default 1}])))
  (is (thrown-with-msg?
       Throwable
       #"Default value of ':foo' option has the wrong type.  'int' type is expected and default value is 'true':"
       (plugin/gm-option [:foo {:type "int"
                                :description "foo-description"
                                :default true}])))
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
  (is (thrown-with-msg?
       Throwable
       #"':foo' option cannot be 'multi'.  Only options of type 'string' and 'int' can:"
       (plugin/gm-option [:foo {:type "bool"
                                :description "foo-description"
                                :multi true}])))
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
          :deprecated true}))
  ;; an option cannot be dynamic and multi at the same time
  (is (thrown-with-msg?
       Throwable
       #"':foo' option cannot be multi and dynamic at the same time:"
       (plugin/gm-option [:foo {:type "string"
                                :description "foo-description"
                                :multi true
                                :dynamic true}]))))

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
                 :fn (fn [params req plugin])}})
         [{:name "foo" :usage "usage" :description "description"}]))
  (is (= (plugin/gm-rpcmethods
          {:foo-1 {:fn (fn [params req plugin])}
           :foo-2 {:usage "usage-2"
                   :fn (fn [params req plugin])}
           :foo-3 {:description "description-3"
                   :fn (fn [params req plugin])}
           :foo-4 {:usage "usage-4"
                   :description "description-4"
                   :fn (fn [params req plugin])}
           :foo-5 {:usage "usage-5"
                   :description "description-5"
                   :long_description "long-description-5"
                   :fn (fn [params req plugin])}
           :foo-6 {:fn (fn [params req plugin])
                   :deprecated true}})
         [{:name "foo-1" :usage "" :description ""}
          {:name "foo-2" :usage "usage-2" :description ""}
          {:name "foo-3" :usage "" :description "description-3"}
          {:name "foo-4" :usage "usage-4" :description "description-4"}
          {:name "foo-5" :usage "usage-5" :description "description-5"
           :long_description "long-description-5"}
          {:name "foo-6" :usage "" :description "" :deprecated true}]))
  ;; cannot declare methods which are already lightningd internal methods
  (is (thrown-with-msg?
       Throwable
       #"You cannot define ':getinfo' method which is already a lightningd internal method."
       (plugin/gm-rpcmethods {:getinfo {}})))
  ;; cannot declare methods which are already lightningd hooks
  (is (thrown-with-msg?
       Throwable
       #"You cannot define ':peer_connected' method which is already a lightningd hook."
       (plugin/gm-rpcmethods {:peer_connected {}})))
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

(deftest gm-notifications-test
  (is (= (plugin/gm-notifications nil) nil))
  (is (= (plugin/gm-notifications ["foo-1" "foo-2" "foo-3"])
         [{:method "foo-1"} {:method "foo-2"} {:method "foo-3"}]))
  ;; Don't let the user declare "log", "progress" or "message" notification
  ;; topics to lightningd.  If they do so, they may are going to use there
  ;; own functions to send those notifications which may result in lightningd
  ;; shutting down the plugin with
  ;;
  ;;     {
  ;;      "code": -4,
  ;;      "message": "Plugin terminated before replying to RPC call."
  ;;     }
  ;;
  ;; response and something like this in the log file (for a "message"
  ;; topic with no "id" fields in the params field)
  ;;
  ;;     2024-03-21T09:39:29.127Z UNUSUAL plugin-notifications: Killing plugin: JSON-RPC notify "id"-field is not present
  ;;
  ;; if they don't respect params field of the JSON RPC notifications
  ;; expected by lightningd for those 3 specific topics.
  (is (thrown-with-msg?
       Throwable
       #"Remove 'log' from :notifications vector."
       (plugin/gm-notifications ["log"])))
  (is (thrown-with-msg?
       Throwable
       #"Remove 'message' from :notifications vector."
       (plugin/gm-notifications ["message"])))
  (is (thrown-with-msg?
       Throwable
       #"Remove 'progress' from :notifications vector."
       (plugin/gm-notifications ["progress"]))))

(deftest gm-subscriptions-test
  (is (= (plugin/gm-subscriptions nil) nil))
  (is (= (plugin/gm-subscriptions
          {:foo-0 {:fn (fn [params req plugin])}
           :foo-1 {:fn (fn [params req plugin])}
           :foo-2 {:fn (fn [params req plugin])}})
         ["foo-0" "foo-1" "foo-2"]))
  (is (= (plugin/gm-subscriptions
          {:foo-0 {:fn (fn [params req plugin])}
           :foo-1 {:fn (fn [params req plugin])}
           :* {:fn (fn [params req plugin])}})
         ["*"]))
  ;; :fn is not defined
  (is (thrown-with-msg?
       Throwable
       #":fn is not defined for ':foo' notification topic in :subscriptions map."
       (plugin/gm-subscriptions {:foo {}})))
  ;; error because :fn is not a function
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' notification topic in :subscriptions map.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (plugin/gm-subscriptions {:foo {:fn [:a-vector "is not a function"]}})))
  ;; error because :fn is not a function (we don't allow symbol as value of :fn)
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' notification topic in :subscriptions map.  :fn must be a function not 'some-symbol' which is an instance of 'class clojure.lang.Symbol'"
       (plugin/gm-subscriptions {:foo {:fn 'some-symbol}}))))

(deftest gm-hooks-test
  (is (= (plugin/gm-hooks nil) nil))
  (is (= (plugin/gm-hooks
          {:foo-0 {:fn (fn [params req plugin])}
           :foo-1 {:before ["bar-1" "baz-1"]
                   :fn (fn [params req plugin])}
           :foo-2 {:after ["bar-2" "baz-2"]
                   :fn (fn [params req plugin])}
           :foo-3 {:before ["bar-3"]
                   :after ["baz-3"]
                   :fn (fn [params req plugin])}})
         [{:name "foo-0"}
          {:name "foo-1" :before ["bar-1" "baz-1"]}
          {:name "foo-2" :after ["bar-2" "baz-2"]}
          {:name "foo-3" :before ["bar-3"] :after ["baz-3"]}]))

  ;; :fn is not defined
  (is (thrown-with-msg?
       Throwable
       #":fn is not defined for ':foo' hook :hooks map."
       (plugin/gm-hooks {:foo {}})))
  ;; error because :fn is not a function
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' hook in :hooks map.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (plugin/gm-hooks {:foo {:fn [:a-vector "is not a function"]}})))
  ;; error because :fn is not a function (we don't allow symbol as value of :fn)
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' hook in :hooks map.  :fn must be a function not 'some-symbol' which is an instance of 'class clojure.lang.Symbol'"
       (plugin/gm-hooks {:foo {:fn 'some-symbol}}))))

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
  ;; options, rpcmethods, subscriptions, notifications and hooks not empty
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
                      :rpcmethods {:foo-1 {:fn (fn [params req plugin])}
                                   :foo-2 {:usage "usage-2"
                                           :fn (fn [params req plugin])}
                                   :foo-3 {:description "description-3"
                                           :fn (fn [params req plugin])}
                                   :foo-4 {:usage "usage-4"
                                           :description "description-4"
                                           :fn (fn [param plugin])}}
                      :subscriptions {:foo-0 {:fn (fn [params req plugin])}
                                      :foo-1 {:fn (fn [params req plugin])}
                                      :foo-2 {:fn (fn [params req plugin])}}
                      :hooks {:foo-0 {:fn (fn [params req plugin])}
                              :foo-1 {:before ["bar-1" "baz-1"]
                                      :fn (fn [params req plugin])}
                              :foo-2 {:after ["bar-2" "baz-2"]
                                      :fn (fn [params req plugin])}
                              :foo-3 {:before ["bar-3"]
                                      :after ["baz-3"]
                                      :fn (fn [params req plugin])}}
                      :notifications ["topic-1" "topic-2" "topic-3"]
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
                   :subscriptions ["foo-0" "foo-1" "foo-2"]
                   :hooks [{:name "foo-0"}
                           {:name "foo-1" :before ["bar-1" "baz-1"]}
                           {:name "foo-2" :after ["bar-2" "baz-2"]}
                           {:name "foo-3" :before ["bar-3"] :after ["baz-3"]}]
                   :notifications [{:method "topic-1"} {:method "topic-2"} {:method "topic-3"}]
                   :dynamic true}}))
  ;; error because :fn is not a function for foo rpcmethod
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' RPC method definition.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (let [plugin (atom {:options {}
                           :rpcmethods {:foo {:fn [:a-vector "is not a function"]}}
                           :dynamic true})
             req {:id 16}]
         (plugin/gm-resp req plugin))))
  ;; error because :fn is not a function for foo subscription
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' notification topic in :subscriptions map.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (let [plugin (atom {:options {}
                           :rpcmethods {}
                           :dynamic true
                           :subscriptions {:foo {:fn [:a-vector "is not a function"]}}})
             req {:id 16}]
         (plugin/gm-resp req plugin))))
  ;; error because :fn is not a function for foo hook
  (is (thrown-with-msg?
       Throwable
       #"Error in ':foo' hook in :hooks map.  :fn must be a function not '\[:a-vector \"is not a function\"\]' which is an instance of 'class clojure.lang.PersistentVector'"
       (let [plugin (atom {:options {}
                           :rpcmethods {}
                           :dynamic true
                           :hooks (plugin/gm-hooks {:foo {:fn [:a-vector "is not a function"]}})})
             req {:id 16}]
         (plugin/gm-resp req plugin))))
  ;; "log", "message", "progress" notifications not to be declared
  (is (thrown-with-msg?
       Throwable
       #"Remove 'progress' from :notifications vector."
       (let [plugin (atom {:options {}
                           :rpcmethods {}
                           :dynamic true
                           :notifications ["progress"]})
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
                      :getmanifest {:allow-deprecated-apis false}})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (is (= (plugin/process-init! req plugin)
           {:jsonrpc "2.0" :id 0 :result {}}))
    (is (= (dissoc @plugin :rpcmethods)
           {:options {}
            :dynamic true
            :getmanifest {:allow-deprecated-apis false}
            :init {:options {}
                   :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                   :rpc-file "lightning-rpc"}}
            :socket-file "/tmp/l1-regtest/regtest/lightning-rpc"}))
    (is (fn? (get-in @plugin [:rpcmethods :setconfig :fn]))))

  ;; :init-fn function is ok
  (let [plugin (atom {:options {:foo nil
                                :bar {:default "bar-default"}
                                :baz {:dynamic true}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin]
                                 (swap! plugin assoc-in [:set-by-init-fn] "init-fn"))})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"
                                :bar "bar-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (is (= (plugin/process-init! req plugin)
           {:jsonrpc "2.0" :id 0 :result {}}))
    (is (= (dissoc @plugin :rpcmethods :init-fn)
           {:options {:foo {:value "foo-value"}
                      :bar {:default "bar-default"
                            :value "bar-value"}
                      :baz {:dynamic true}}
            :dynamic true
            :getmanifest {:allow-deprecated-apis false}
            :init {:options {:foo "foo-value"
                             :bar "bar-value"}
                   :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                   :rpc-file "lightning-rpc"}}
            :socket-file "/tmp/l1-regtest/regtest/lightning-rpc"
            :set-by-init-fn "init-fn"}))
    (is (fn? (get-in @plugin [:rpcmethods :setconfig :fn]))))
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
                      :getmanifest {:allow-deprecated-apis false}})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (is (= (plugin/process-init! req plugin)
           {:jsonrpc "2.0" :id 0 :result {:disable "Wrong option 'foo'"}})))
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
                      :getmanifest {:allow-deprecated-apis false}})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]

    (is (= (plugin/process-init! req plugin)
           {:jsonrpc "2.0" :id 0 :result {:disable "Wrong option 'foo'"}})))

  ;; :check-opt is not a function
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt [:a-vector "is not a function"]}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (is (= (plugin/process-init! req plugin)
           {:jsonrpc "2.0" :id 0 :result {:disable ":check-opt of ':foo' option must be a function not '[:a-vector \"is not a function\"]' which is an instance of 'class clojure.lang.PersistentVector'"}})))

  ;; :check-opt throws an error at execution time
  (let [plugin (atom {:options
                      {:foo
                       {:check-opt (fn [value plugin] (/ 1 0))}}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {:foo "foo-value"}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]
    (is (re-find
         #"(?s):check-opt of ':foo' option thrown the following exception when called with 'foo-value' value:.*#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
         (get-in (plugin/process-init! req plugin) [:result :disable]))))
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

    (is (= (plugin/process-init! req plugin)
           {:jsonrpc "2.0" :id 0 :result {:disable ":init-fn must be a function not 'not-a-function' which is an instance of 'class clojure.lang.Symbol'"}})))
  ;; :init-fn throws an error
  (let [plugin (atom {:options {}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin] (/ 1 0))})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]

    (is (re-find
         #"(?s)#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
         (get-in (plugin/process-init! req plugin) [:result :disable]))))
  ;; :init-fn disable the plugin
  (let [plugin (atom {:options {}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin]
                                 (throw (ex-info "disabled by user" {})))})
        req {:jsonrpc "2.0" :id 0 :method "init"
             :params {:options {}
                      :configuration {:lightning-dir "/tmp/l1-regtest/regtest"
                                      :rpc-file "lightning-rpc"}}}]

    (is (= (-> (plugin/process-init! req plugin)
               :result :disable str/split-lines first)
           "disabled by user"))))

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
        resp (plugin/setconfig! params nil plugin)]
    (is (= resp {}))
    (is (= @plugin {:options {:foo {:value "foo-value"
                                    :dynamic true}}})))
  (let [plugin (atom {:options {:foo {:type "int"
                                      :dynamic true}}})
        params-0 {:config "foo", :val "12"}
        params-1 {:config "foo", :val "-12"}]
    (is (= (plugin/setconfig! params-0 nil plugin) {}))
    (is (= @plugin {:options {:foo {:value 12
                                    :type "int"
                                    :dynamic true}}}))
    (is (= (plugin/setconfig! params-1 nil plugin) {}))
    (is (= @plugin {:options {:foo {:value -12
                                    :type "int"
                                    :dynamic true}}})))
  (let [plugin (atom {:options {:foo {:type "bool"
                                      :dynamic true}}})
        params-0 {:config "foo", :val "true"}
        params-1 {:config "foo", :val "false"}]
    (is (= (plugin/setconfig! params-0 nil plugin) {}))
    (is (= @plugin {:options {:foo {:value true
                                    :type "bool"
                                    :dynamic true}}}))
    (is (= (plugin/setconfig! params-1 nil plugin) {}))
    (is (= @plugin {:options {:foo {:value false
                                    :type "bool"
                                    :dynamic true}}})))
  (let [plugin (atom {:options {:foo {:type "flag"
                                      :dynamic true}}})
        params {:config "foo"}]
    (is (= (plugin/setconfig! params nil plugin) {}))
    (is (= @plugin {:options {:foo {:value true
                                    :type "flag"
                                    :dynamic true}}})))
  (try
    (let [plugin (atom {:options {:foo nil}})
          params {:config "foo", :val "foo-value"}]
      (plugin/setconfig! params nil plugin))
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
    (is (= (plugin/setconfig! params-ok nil plugin) {}))
    (is (= (get-in @plugin [:options :foo :value]) 12))
    (try
      (plugin/setconfig! params-wrong nil plugin)
      (catch Exception e
        (is (= (ex-data e)
               {:error {:code -32602 :message "'foo' option must be positive"}})))))
  (try
    (let [plugin (atom {:options
                        {:foo
                         {:dynamic true
                          :check-opt [:a-vector "is not a function"]}}})
          params {:config "foo", :val "foo-value"}]
      (plugin/setconfig! params nil plugin))
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
      (plugin/setconfig! params nil plugin))
    (catch Exception e
      ;; (prn e)
      (is (re-find
           #"(?s):check-opt of ':foo' option thrown the following exception when called with 'foo-value' value:.*#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (get-in (ex-data e) [:error :message]))))))

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
    ;; test that notify returns nil so that it can be used
    ;; as last expression in :fn of RPC methods which expect
    ;; a json writable object as last expression
    (is (nil? (plugin/log message plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "log"
            :params {:level "info" :message "foo"}})))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "bar"
        level "debug"]
    (is (nil? (plugin/log message level plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "log"
            :params {:level "debug" :message "bar"}})))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "foo-1\nfoo-2\nfoo-3\n"]
    (plugin/log message plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (let [srdr (java.io.StringReader. (str (:_out @plugin)))
                 pbr (java.io.PushbackReader. srdr 64)]
             (for [_ (range 3)]
               (json/read pbr :key-fn keyword)))
           '({:jsonrpc "2.0", :method "log", :params {:level "info", :message "foo-1"}}
             {:jsonrpc "2.0", :method "log", :params {:level "info", :message "foo-2"}}
             {:jsonrpc "2.0", :method "log", :params {:level "info", :message "foo-3"}}))))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message 'not-a-string]
    (is (thrown-with-msg?
         Throwable
         #"Assert failed: \(string\? message\)"
         (plugin/log message plugin)))))

(deftest notify-test
  ;; In these tests, we don't specify :notifications in plugin,
  ;; because clnplugin-clj doesn't check if we've declared the
  ;; notifications to lightningd during the getmanifest round.  We
  ;; just send the notification always.  lightningd will ignore it
  ;; if it had to and log a message.
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        topic "foo" params {:bar "baz"}]
    ;; test that notify returns nil so that it can be used
    ;; as last expression in :fn of RPC methods which expect
    ;; a json writable object as last expression
    (is (nil? (plugin/notify topic params plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0" :method topic :params params})))
  ;; non json writable in :params of the notification we
  ;; try to send to lightningd.  So we log it
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        topic "foo" params (atom nil)]
    (plugin/notify topic params plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100)
    (let [outs (str/split (str (:_out @plugin)) #"\n\n")
          logs (map #(json/read-str % :key-fn keyword) outs)]
      ;; logs
      (is (some #(re-find #"Error while sending notification.*:method.*foo"
                          (get-in % [:params :message]))
                logs))
      (is (some #(re-find #"#error" (get-in % [:params :message]))
                logs))
      (is (some #(re-find #".*Don't know how to write JSON of class clojure.lang.Atom"
                          (get-in % [:params :message]))
                logs)))))

(deftest notify-message-test
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "foo"
        req {:id 16}]
    (is (nil? (plugin/notify-message message req plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "message"
            :params {:id 16 :level "info" :message "foo"}})))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message "foo"
        level "debug"
        req {:id 16}]
    (is (nil? (plugin/notify-message message level req plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "message"
            :params {:id 16 :level "debug" :message "foo"}})))
  ;; error if message is not a string
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        message 'not-a-string
        req {:id 16}]
    (is (thrown-with-msg?
         Throwable
         #"Assert failed: \(string\? message\)"
         (plugin/notify-message message req plugin)))))

(deftest notify-progress-test
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        step 0
        total-steps 3
        req {:id 16}]
    (is (nil? (plugin/notify-progress step total-steps req plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "progress"
            :params {:id 16 :num 0 :total 3}})))
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        step 1 total-steps 3
        stage 1 total-stages 5
        req {:id 16}]
    (is (nil? (plugin/notify-progress step total-steps stage total-stages req plugin)))
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (is (= (json/read-str (str (:_out @plugin)) :key-fn keyword)
           {:jsonrpc "2.0"
            :method "progress"
            :params {:id 16 :num 1 :total 3
                     :stage {:num 1 :total 5}}})))
  ;; error: step must be < to total-steps
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        step 3
        total-steps 3
        req {:id 16}]
    (is (thrown-with-msg?
         Throwable
         #"Assert failed"
         (plugin/notify-progress step total-steps req plugin)))
    )
  ;; error: stage must be < to total-stages
  (let [plugin (atom {:_resps (agent nil) :_out (new java.io.StringWriter)})
        step 1 total-steps 3
        stage 3 total-stages 3
        req {:id 16}]
    (is (thrown-with-msg?
         Throwable
         #"Assert failed"
         (plugin/notify-progress step total-steps stage total-stages req plugin)))))

(deftest process-test
  (let [foo-2 (fn [params req plugin] {:bar-2 "baz-2"})
        plugin (atom {:rpcmethods
                      {:foo-0 {:fn (fn [params req plugin] {:bar "baz"})}
                       :foo-1 {:fn (fn [params req plugin] {:bar-1 (:baz-1 params)})}
                       :foo-2 {:fn foo-2}
                       :foo-3 {:fn (fn [params req plugin] {:bar-3 (:id req)})}
                       :foo-4 {:fn (fn [params req plugin]
                                     (swap! plugin assoc :bar-4 "baz-4")
                                     {})}
                       :foo-5 {:fn (fn [params req plugin]
                                     {:bar-5 (loop [bar-4 nil]
                                               (or bar-4 (recur (:bar-4 @plugin))))})}}})
        req-0 {:jsonrpc "2.0" :id "id-0" :method "foo-0" :params {}}
        req-1 {:jsonrpc "2.0" :id "id-1" :method "foo-1" :params {:baz-1 "baz-1"}}
        req-2 {:jsonrpc "2.0" :id "id-2" :method "foo-2" :params {}}
        req-3 {:jsonrpc "2.0" :id "id-3" :method "foo-3" :params {}}
        req-4 {:jsonrpc "2.0" :id "id-4" :method "foo-4" :params {}}
        req-5 {:jsonrpc "2.0" :id "id-5" :method "foo-5" :params {}}]
    (is (= (second (plugin/process req-0 plugin)) {:jsonrpc "2.0" :id "id-0" :result {:bar "baz"}}))
    (is (= (second (plugin/process req-1 plugin)) {:jsonrpc "2.0" :id "id-1" :result {:bar-1 "baz-1"}}))
    (is (= (second (plugin/process req-2 plugin)) {:jsonrpc "2.0" :id "id-2" :result {:bar-2 "baz-2"}}))
    (is (= (second (plugin/process req-3 plugin)) {:jsonrpc "2.0" :id "id-3" :result {:bar-3 "id-3"}}))
    (is (= (second (plugin/process req-4 plugin)) {:jsonrpc "2.0" :id "id-4" :result {}}))
    (is (= (second (plugin/process req-5 plugin)) {:jsonrpc "2.0" :id "id-5" :result {:bar-5 "baz-4"}})))

  ;; Custom errors raised by user
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params req plugin]
                              (throw
                               (let [msg "custom-error"]
                                 (ex-info msg {:error
                                               {:code -100 :message msg}}))))}}})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (let [[log-msgs resp] (plugin/process req plugin)]
      (is (= resp {:jsonrpc "2.0" :id "some-id"
                   :error {:code -100 :message "custom-error"}}))
      (is (re-find #"Error while processing.*method.*custom-error" (first log-msgs)))
      (is (re-find #"code.*-100.*message.*custom-error" (second log-msgs)))))

  ;; missing :code key in the error thrown by :fn
  ;; so it is set to -32603
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params req plugin]
                              (throw
                               (let [msg "custom-error"]
                                 (ex-info msg {:error {:message msg}}))))}}})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (let [[log-msgs resp] (plugin/process req plugin)]
      (is (= resp {:jsonrpc "2.0" :id "some-id"
                   :error {:code -32603 :message "custom-error"}}))))

  ;; missing :message key in the error thrown by :fn
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params req plugin]
                              (throw
                               (ex-info "custom-error" {:error {:code -100}})))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (let [[log-msgs resp] (plugin/process req plugin)]
      (is (= (get-in resp [:error :code]) -100))
      (is (re-find #"Error while processing.*method.*custom-error"
                   (get-in resp [:error :message])))))

  ;; empty data in the error thrown by :fn
  (let [plugin (atom {:rpcmethods
                      {:custom-error
                       {:fn (fn [params req plugin]
                              (throw
                               (ex-info "custom-error" {})))}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req {:jsonrpc "2.0" :id "some-id" :method "custom-error" :params {}}]
    (let [[log-msgs resp] (plugin/process req plugin)]
      (is (= (get-in resp [:error :code]) -32603))
      (is (re-find #"Error while processing.*method.*custom-error"
                   (get-in resp [:error :message])))))

  ;; Execution errors
  (let [plugin (atom {:rpcmethods
                      {:execution-error
                       {:fn (fn [params req plugin] (/ 1 0))}}})
        req {:jsonrpc "2.0" :id "some-id" :method "execution-error" :params {}}]
    (let [[log-msgs resp] (plugin/process req plugin)]
      (is (= (get-in resp [:error :code]) -32603))
      (is (re-find #"Error while processing.*method.*execution-error"
                   (get-in resp [:error :message])))
      (is (re-find
           #"(?s)#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"
           (get-in resp [:error :exception])))
      (is (re-find #"Error while processing.*method.*execution-error" (first log-msgs)))
      (is (re-find #" :cause \"Divide by zero\"" (second log-msgs)))))

  ;; AssertionError
  (let [plugin (atom {:rpcmethods
                      {:assertion-error
                       {:fn (fn [params req plugin] (assert (< 0 -1)))}}})
        req {:jsonrpc "2.0" :id "some-id" :method "assertion-error" :params {}}]
    (let [[log-msgs resp] (plugin/process req plugin)]
      (is (= (get-in resp [:error :code]) -32603))
      (is (re-find #"Error while processing.*method.*assertion-error"
                   (get-in resp [:error :message])))
      (is (re-find
           #"(?s)#error.*:cause.*Assert failed: \(< 0 -1\)"
           (get-in resp [:error :exception])))
      (is (re-find #"Error while processing.*method.*assertion-error" (first log-msgs)))
      (is (re-find #" :cause \"Assert failed: \(< 0 -1\)\"" (second log-msgs)))))

  ;; subscriptions
  (let [plugin (atom {:subscriptions
                      {:foo-0 {:fn (fn [params req plugin]
                                     (swap! plugin assoc :foo-0 "bar-0"))}
                       :foo-1 {:fn (fn [params req plugin]
                                     (swap! plugin assoc :foo-1 params))}
                       :* {:fn (fn [params req plugin]
                                 (swap! plugin assoc :* ["all" (:method req)]))}}})
        req-0 {:jsonrpc "2.0" :method "foo-0" :params {}}
        req-1 {:jsonrpc "2.0" :method "foo-1" :params {:bar-1 "baz-1"}}
        req-2 {:jsonrpc "2.0" :method "foo-2" :params {}}]
    (is (= (plugin/process req-0 plugin) [nil nil]))
    (is (= (plugin/process req-1 plugin) [nil nil]))
    (is (= (plugin/process req-2 plugin) [nil nil]))
    (is (= (:foo-0 @plugin) "bar-0"))
    (is (= (:foo-1 @plugin) {:bar-1 "baz-1"}))
    (is (= (:* @plugin) ["all" "foo-2"])))

  ;; subscription error - receive a notification with no corresponding subscription
  (let [plugin (atom {:subscriptions {}})
        req {:jsonrpc "2.0" :method "foo" :params {}}
        [log-msgs resp] (plugin/process req plugin)]
    (is (= resp nil))
    (is (re-find #"Error while processing.*method.*foo" (first log-msgs)))
    (is (re-find #":cause.*Cannot.*invoke.*clojure.lang.IFn.invoke.*because.*method_fn.*is.*null" (second log-msgs))))

  ;; hooks
  (let [plugin (atom {:hooks
                      {:some-hook {:fn (fn [params req plugin] {:result "continue"})}}})
        req {:jsonrpc "2.0" :id "some-id" :method "some-hook" :params {}}]
    (is (= (second (plugin/process req plugin)) {:jsonrpc "2.0" :id "some-id" :result {:result "continue"}})))

  ;; hook error - receive a notification with no corresponding subscription
  (let [plugin (atom {:hooks {}})
        req {:jsonrpc "2.0" :id "some-id" :method "peer_connected" :params {}}
        [log-msgs resp] (plugin/process req plugin)]
    (is (= (get-in resp [:error :code]) -32603))
    (is (re-find #"Error while processing.*method.*peer_connected"
                 (get-in resp [:error :message])))
    (is (re-find #"Error while processing.*method.*peer_connected" (first log-msgs)))
    (is (re-find #":cause.*Cannot.*invoke.*clojure.lang.IFn.invoke.*because.*method_fn.*is.*null" (second log-msgs)))))

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

(deftest max-parallel-reqs-test
  (let [plugin-0 (atom {})
        plugin-1 (atom {:max-parallel-reqs -12})
        plugin-2 (atom {:max-parallel-reqs 32})
        plugin-3 (atom {:max-parallel-reqs 2048})
        plugin-4 (atom {:max-parallel-reqs 'not-an-int})]
    (is (= (plugin/max-parallel-reqs plugin-0) 512))
    (is (= (plugin/max-parallel-reqs plugin-1) 1))
    (is (= (plugin/max-parallel-reqs plugin-2) 32))
    (is (= (plugin/max-parallel-reqs plugin-3) 1023))
    (is (= (plugin/max-parallel-reqs plugin-4) 512))))

(deftest params->map-test
  ;; params is {}
  (let [params {}]
    (is (= (plugin/params->map [] params) params))
    (is (= (plugin/params->map [:foo] params) params))
    (is (= (plugin/params->map [:foo :bar] params) params))
    (is (= (plugin/params->map [:foo :bar :baz] params) params)))
  ;; params is {:foo "foo-value"}
  (let [params {:foo "foo-value"}]
    (is (= (plugin/params->map [] params) {}))
    (is (= (plugin/params->map [:foo] params) params))
    (is (= (plugin/params->map [:foo :bar] params) params))
    (is (= (plugin/params->map [:foo :bar :baz] params) params)))
  ;; params is {:foo "foo-value" :bar "bar-value"}
  (let [params {:foo "foo-value" :bar "bar-value"}]
    (is (= (plugin/params->map [] params) {}))
    (is (= (plugin/params->map [:foo] params) {:foo "foo-value"}))
    (is (= (plugin/params->map [:foo :bar] params) params))
    (is (= (plugin/params->map [:foo :bar :baz] params) params)))
  ;; params is []
  (let [params []]
    (is (= (plugin/params->map [] params) {}))
    (is (= (plugin/params->map [:foo] params) {}))
    (is (= (plugin/params->map [:foo :bar] params) {}))
    (is (= (plugin/params->map [:foo :bar :baz] params) {})))
  ;; params is ["foo-value"]
  (let [params ["foo-value"]]
    (is (= (plugin/params->map [] params) {}))
    (is (= (plugin/params->map [:foo] params) {:foo "foo-value"}))
    (is (= (plugin/params->map [:foo :bar] params) {:foo "foo-value"}))
    (is (= (plugin/params->map [:foo :bar :baz] params) {:foo "foo-value"})))
  ;; params is ["foo-value" "bar-value"]
  (let [params ["foo-value" "bar-value"]]
    (is (= (plugin/params->map [] params) {}))
    (is (= (plugin/params->map [:foo] params) {:foo "foo-value"}))
    (is (= (plugin/params->map [:foo :bar] params) {:foo "foo-value" :bar "bar-value"}))
    (is (= (plugin/params->map [:foo :bar :baz] params) {:foo "foo-value" :bar "bar-value"}))))
