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
                 :fn 'fn-foo}})
         [{:name "foo" :usage "usage" :description "description"}]))
  (is (= (plugin/gm-rpcmethods
          {:foo-1 {:fn 'fn-foo-1}
           :foo-2 {:usage "usage-2"
                   :fn 'fn-foo-2}
           :foo-3 {:description "description-3"
                   :fn 'fn-foo-3}
           :foo-4 {:usage "usage-4"
                   :description "description-4"
                   :fn 'fn-foo-4}})
         [{:name "foo-1" :usage "" :description ""}
          {:name "foo-2" :usage "usage-2" :description ""}
          {:name "foo-3" :usage "" :description "description-3"}
          {:name "foo-4" :usage "usage-4" :description "description-4"}])))

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
                      :rpcmethods {:foo-1 {:fn 'fn-foo-1}
                                   :foo-2 {:usage "usage-2"
                                           :fn 'fn-foo-2}
                                   :foo-3 {:description "description-3"
                                           :fn 'fn-foo-3}
                                   :foo-4 {:usage "usage-4"
                                           :description "description-4"
                                           :fn 'fn-foo-4}}
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
  ;; raise error due to wrong options
  (is (thrown? Throwable
               (let [plugin (atom {:options {:foo {:type "bool"
                                                   :description "foo-description"
                                                   :multi true}}
                                   :rpcmethods {}
                                   :dynamic true})
                     req {:id 16}]
                 (plugin/gm-resp plugin req)))))

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
                      :rpcmethods {:foo 'foo}
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

(deftest set-option!-test
  (is (thrown-with-msg?
       Throwable
       #"Cannot set ':foo' option which has not been declared to lightningd"
       (let [plugin (atom {:options {}})]
         (plugin/set-option! [:foo "foo-value"] plugin :at-init))))
  (is (thrown-with-msg?
       Throwable
       #"Cannot set ':foo' option which has not been declared to lightningd"
       (let [plugin (atom {:options {}})]
         (plugin/set-option! [:foo "foo-value"] plugin))))
  (is (= (let [plugin (atom {:options {:foo nil}})]
           (plugin/set-option! [:foo "foo-value"] plugin :at-init)
           @plugin)
         {:options {:foo {:value "foo-value"}}}))
  (is (= (let [plugin (atom {:options {:foo {:dynamic true}}})]
           (plugin/set-option! [:foo "foo-value"] plugin)
           @plugin)
         {:options {:foo {:dynamic true
                          :value "foo-value"}}}))
  (is (thrown-with-msg?
       Throwable
       #"Cannot set ':foo' option which is not dynamic.  Add ':dynamic true' to its declaration."
       (let [plugin (atom {:options {:foo nil}})]
         (plugin/set-option! [:foo "foo-value"] plugin))))
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
                          :value "bar-value"}}})))

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
                          :value "baz-value"}}})))

(deftest process-init!-test
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
      (is (= (-> resp :result :disable str/split-lines first)
             "java.lang.ArithmeticException: Divide by zero"))))
  (let [plugin (atom {:options {}
                      :rpcmethods {}
                      :dynamic true
                      :getmanifest {:allow-deprecated-apis false}
                      :init-fn (fn [req plugin] {:disable "disabled by user"})
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
  (let [plugin (atom {:options {:foo {:dynamic true}}})
        params {:config "foo", :val "foo-value"}
        resp (plugin/setconfig! params plugin)]
    (is (= resp {}))
    (is (= @plugin {:options {:foo {:value "foo-value"
                                    :dynamic true}}})))
  ;; 'foo' option is not dynamic
  (is (thrown?
       Throwable
       (let [plugin (atom {:options {:foo nil}})
             params {:config "foo", :val "foo-value"}]
         (plugin/setconfig! params plugin)))))

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
                       :foo-4 {:fn (fn [params plugin]
                                     (swap! plugin assoc :bar-4 "baz-4")
                                     {})}
                       :foo-5 {:fn (fn [params plugin]
                                     {:bar-5 (loop [bar-4 nil]
                                               (or bar-4 (recur (:bar-4 @plugin))))})}}
                      :_out (new java.io.StringWriter)
                      :_resps (agent nil)})
        req-0 {:jsonrpc "2.0" :id "id-0" :method "foo-0" :params {}}
        req-1 {:jsonrpc "2.0" :id "id-1" :method "foo-1" :params {:baz-1 "baz-1"}}
        req-2 {:jsonrpc "2.0" :id "id-2" :method "foo-2" :params {}}
        req-4 {:jsonrpc "2.0" :id "id-4" :method "foo-4" :params {}}
        req-5 {:jsonrpc "2.0" :id "id-5" :method "foo-5" :params {}}]
    (plugin/process req-0 plugin)
    (Thread/sleep 100) ;; because `plugin/process` calls are asynchronous (in go blocks)
    (plugin/process req-1 plugin)
    (Thread/sleep 100)
    (plugin/process req-2 plugin)
    (Thread/sleep 100)
    (plugin/process req-4 plugin)
    (Thread/sleep 100)
    (plugin/process req-5 plugin)
    (await (:_resps @plugin))
    (Thread/sleep 100) ;; if we don't wait, :_out would be empty
    (let [outs (-> (:_out @plugin) str (str/split #"\n\n"))
          resps (map #(json/read-str % :key-fn keyword) outs)]
      (is (= resps
             '({:jsonrpc "2.0" :id "id-0" :result {:bar "baz"}}
               {:jsonrpc "2.0" :id "id-1" :result {:bar-1 "baz-1"}}
               {:jsonrpc "2.0" :id "id-2" :result {:bar-2 "baz-2"}}
               {:jsonrpc "2.0" :id "id-4" :result {}}
               {:jsonrpc "2.0" :id "id-5" :result {:bar-5 "baz-4"}})))))

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
  ;; so it is set to -326000
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
              {:code -32600 :message "custom-error"}}))))
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
      (is (= (get-in resp [:error :code]) -32600))
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
      (is (= (get-in resp [:error :code]) -32600))
      (is (re-find #"Error while processing.*method.*execution-error"
                   (get-in resp [:error :message])))
      (is (= (-> resp :error :stacktrace str/split-lines first)
             "java.lang.ArithmeticException: Divide by zero"))
      (is (some #(re-find #"Error while processing.*method.*execution-error"
                          (get-in % [:params :message]))
                resp-and-logs))
      (is (some #(= % {:jsonrpc "2.0"
                       :method "log"
                       :params {:level "debug"
                                :message "java.lang.ArithmeticException: Divide by zero"}})
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
  (is (thrown-with-msg?
       Throwable
       #"Invalid token in json input: 'foo'"
       (let [req-str "foo\n\n"]
         (with-open [in (-> (java.io.StringReader. req-str) clojure.lang.LineNumberingPushbackReader.)]
           (plugin/read in))))))
