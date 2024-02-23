(ns clnplugin-clj-test
  "Test clnplugin-clj library."
  (:require [clojure.test :refer :all])
  (:require [clnplugin-clj :as plugin]))

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
       #"Wrong type 'not-a-type' for option 'foo'.  Authorized types are: string, int, bool, flag."
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
       #"Default value of 'foo' option has the wrong type.  'string' type is expected and default value is '1'"
       (plugin/gm-option [:foo {:type "string"
                                :description "foo-description"
                                :default 1}])))
  (is (thrown-with-msg?
       Throwable
       #"Default value of 'foo' option has the wrong type.  'string' type is expected and default value is '1'"
       (plugin/gm-option [:foo {:description "foo-description"
                                :default 1}])))
  (is (thrown-with-msg?
       Throwable
       #"Default value of 'foo' option has the wrong type.  'int' type is expected and default value is 'true'"
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
       #"'foo' option cannot be 'multi'.  Only options of type 'string' and 'int' can."
       (plugin/gm-option [:foo {:type "bool"
                                :description "foo-description"
                                :multi true}]))))

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
           :multi true}]))
  (is (thrown? Throwable
               (= (plugin/gm-options {:foo-1 nil
                                      :foo-2 {:type "string"
                                              :description "foo-2-description"
                                              :default 1}})))))

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

(deftest getmanifest!-test
  (is (= (let [plugin (atom nil)
               req {:params {:allow-deprecated-apis false}}]
           (plugin/getmanifest! plugin req)
           @plugin)
         {:getmanifest {:params {:allow-deprecated-apis false}}})))

(deftest getmanifest-resp-test
  ;; defaults
  (is (= (let [plugin (atom {:options {}
                             :rpcmethods {}
                             :dynamic true})
               req {:id 16}]
           (plugin/getmanifest-resp plugin req))
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
           (plugin/getmanifest-resp plugin req))
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
           (plugin/getmanifest-resp plugin req))
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
                 (plugin/getmanifest-resp plugin req)))))

(deftest default!-test
  (is (= (let [plugin (atom nil)]
           (plugin/default! plugin)
           @plugin)
         {:options {}
          :rpcmethods {}
          :dynamic true}))
  (is (= (let [plugin (atom {:options {:opt1 'opt1}})]
           (plugin/default! plugin)
           @plugin)
         {:options {:opt1 'opt1}
          :rpcmethods {}
          :dynamic true}))
  (is (= (let [plugin (atom {:rpcmethods {:foo 'foo}})]
           (plugin/default! plugin)
           @plugin)
         {:options {}
          :rpcmethods {:foo 'foo}
          :dynamic true}))
  (is (= (let [plugin (atom {:options {:opt1 'opt1}
                             :rpcmethods {:foo 'foo}})]
           (plugin/default! plugin)
           @plugin)
         {:options {:opt1 'opt1}
          :rpcmethods {:foo 'foo}
          :dynamic true}))
  (is (= (let [plugin (atom {:dynamic false})]
           (plugin/default! plugin)
           @plugin)
         {:options {}
          :rpcmethods {}
          :dynamic false}))
  (is (= (let [plugin (atom {:options {:opt1 'opt1}
                             :rpcmethods {:foo 'foo}
                             :dynamic false})]
           (plugin/default! plugin)
           @plugin)
         {:options {:opt1 'opt1}
          :rpcmethods {:foo 'foo}
          :dynamic false})))
