(ns clnplugin-clj
  "Core Lightning plugin library for Clojure."
  (:refer-clojure :exclude [read])
  (:require [clojure.data.json :as json])
  (:require [clojure.core.async :refer [go]]))

(defn gm-option
  "Check option and return it in a format understable by lightningd.

  - TYPE and DESCRIPTION are mandatory.
  - TYPE can only be:
    - \"string\": a string
    - \"int\": parsed as a signed integer (64-bit)
    - \"bool\": a boolean
    - \"flag\": no-arg flag option.  Presented as true if config specifies it.
  - If TYPE is string or int, MULTI can be set to true which indicates
    that the option can be specified multiple times.  These will always be represented
    in the init request as a (possibly empty) JSON array.

  If TYPE is not specified, set it to \"string\".

  If DESCRIPTION is not specified, set it to \"\".

  If DYNAMIC is true, KW-NAME can be set dynamically with `setconfig`
  CLN command.

  If DEPRECATED is true and the user sets `allow-deprecated-apis` to false,
  KW-NAME option is disabled by lightningd and must not be used by the plugin."
  [[kw-name {:keys [type description default multi dynamic deprecated] :as option}]]
  (let [-name {:name (name kw-name)}
        -type (cond (nil? type)
                    {:type "string"}
                    (some #{type} #{"string" "int" "bool" "flag"})
                    {:type type}
                    true
                    (throw
                     (ex-info
                      (format "Wrong type '%s' for option '%s'.  Authorized types are: string, int, bool, flag."
                              type (name kw-name))
                      {:kw-name kw-name :option option})))
        -description {:description (or description "")}
        types {java.lang.String "string"
               java.lang.Long "int"
               java.lang.Boolean "bool"}
        -default (when default
                   (let [default-type (get types (class default))]
                     (if (= default-type (:type -type))
                       {:default default}
                       (throw
                        (ex-info
                         (format "Default value of '%s' option has the wrong type.  '%s' type is expected and default value is '%s'"
                                 (name kw-name) (:type -type) default)
                         {:kw-name kw-name :option option})))))
        -multi (when multi
                 (if (some #{type} #{"string" "int"})
                   {:multi true}
                   (throw
                    (ex-info
                     (format "'%s' option cannot be 'multi'.  Only options of type 'string' and 'int' can."
                             (name kw-name))
                     {:kw-name kw-name :option option}))))
        -dynamic (and dynamic {:dynamic true})
        -deprecated (and deprecated {:deprecated true})]
    (merge -name -type -description -default -multi -dynamic -deprecated)))

(defn gm-options
  "Return the vector of plugin options meant to be used in the getmanifest response.

  See clnplugin-clj/gm-option."
  [options]
  (mapv gm-option (seq options)))

(defn gm-rpcmethods
  "Return the vector of RPC methods meant to be used in the getmanifest response.

  The \"rpcmethods\" field is required in the getmanifest response:
      - if no method to define, \"rpcmethods\" must be an empty vector,
      - if methods are supplied, they must contain fields \"name\", \"usage\"
        and \"description\""
  [rpcmethods]
  (let [f (fn [[kw-name method]]
            {:name (name kw-name)
             :usage (get method :usage "")
             :description (get method :description "")})]
    (mapv f (seq rpcmethods))))

(defn gm-resp
  "Return the response to the getmanifest REQ.

  PLUGIN can contain any key; only the keys :options, :rpcmethods,
  :subscriptions, :hooks, :featurebits, :notifications, :custommessages,
  :nonnumericids and :dynamic will retained in the response to be
  sent to lightningd.

  Note that only :options and :rpcmethods are mandatory.

  See clnplugin-clj/gm-rpcmethods, clnplugin-clj/gm-options,
  and clnplugin-clj/default! ."
  [req plugin]
  (let [p @plugin]
    {:jsonrpc "2.0"
     :id (:id req)
     :result {:options (gm-options (:options p))
              :rpcmethods (gm-rpcmethods (:rpcmethods p))
              :dynamic (:dynamic p)}}))

(defn default!
  "Set default values for :dynamic, :options and :rpcmethods keys if omitted."
  [plugin]
  (swap! plugin
         (fn [p]
           (-> p
               (update :dynamic #(if (nil? %) true %))
               (update :options #(if (nil? %) {} %))
               (update :rpcmethods #(if (nil? %) {} %))))))

(defn add-req-params-to-plugin!
  "Store params of REQ in PLUGIN with key being keyword of REQ's method.

  Use this to store params received from CLN in \"getmanifest\" and
  \"init\" request."
  [req plugin]
  (let [method (keyword (:method req))
        params (:params req)]
    (swap! plugin #(merge % {method params}))))

(defn process-getmanifest!
  "..."
  [req plugin out]
  (add-req-params-to-plugin! req plugin)
  (json/write (gm-resp req plugin) out :escape-slash false)
  (. out (flush)))

(defn set-option!
  "..."
  ([[kw-opt value] plugin]
   (set-option! [kw-opt value] plugin false))
  ([[kw-opt value] plugin at-init]
   (if (contains? (:options @plugin) kw-opt)
     (if (or at-init
             (get-in @plugin [:options kw-opt :dynamic]))
       (swap! plugin assoc-in [:options kw-opt :value] value)
       (throw
        (let [msg (format "Cannot set '%s' option which is not dynamic.  Add ':dynamic true' to its declaration." kw-opt)]
          (ex-info msg {:error {:code -32600 :message msg}}))))
     (throw
      (let [msg (format "Cannot set '%s' option which has not been declared to lightningd" kw-opt)]
        (ex-info msg {:error {:code -32600 :message msg}}))))))

(defn set-options-at-init!
  "..."
  [options plugin]
  (when-not (empty? options)
    (doseq [opt (seq options)]
      (set-option! opt plugin :at-init))))

(defn process-init!
  "..."
  [req plugin out]
  (let [dir (get-in req [:params :configuration :lightning-dir])
        rpc-file (get-in req [:params :configuration :rpc-file])
        socket-file (str (clojure.java.io/file dir rpc-file))
        options (get-in req [:params :options])
        resp {:jsonrpc "2.0" :id (:id req) :result {}}]
    (swap! plugin assoc-in [:socket-file] socket-file)
    (set-options-at-init! options plugin)
    (add-req-params-to-plugin! req plugin)
    (when-let [init-fn (:init-fn @plugin)]
      (if (fn? init-fn)
        (init-fn req plugin)
        (throw
         (let [msg (format "Cannot initialize plugin.  :init-fn must be a function not '%s' which is an instance of '%s'"
                           init-fn (class init-fn))]
           (ex-info msg {:error {:code -32600 :message msg}})))))
    (json/write resp out :escape-slash false)
    (. out (flush))))

(defn setconfig!
  "..."
  [params plugin]
  (let [kw-opt (keyword (:config params))
        value (:val params)]
    (set-option! [kw-opt value] plugin)
    {}))

(defn add-rpcmethod-to-plugin!
  "..."
  [method fn plugin]
  (swap! plugin assoc-in [:rpcmethods method] {:fn fn}))

(defn write
  "..."
  [_ resp]
  (json/write resp *out* :escape-slash false)
  (flush))

(defn process
  "..."
  [a plugin req]
  (go
    (let [method (keyword (:method req))
          method-fn (get-in (:rpcmethods @plugin) [method :fn])
          resp {:jsonrpc "2.0"
                :id (:id req)
                :result (method-fn (:params req) plugin)}]
      (send a write resp))))

(defn read
  "Read a CLN JSON-RPC request from IN.

  CLN requests end with an empty line \"\\n\\n\"."
  [in]
  (binding [*in* in]
    (loop [req-acc "" line (read-line)]
      (cond
        (nil? line) nil
        (empty? line)
        (try
          (json/read-str req-acc :key-fn keyword)
          (catch Exception e
            (throw
             (let [msg (format "Invalid token in json input: '%s'" req-acc)]
               (ex-info msg {:error {:code -32600 :message msg}})))))
        true (let [next-line (read-line)]
               (recur (str req-acc line) next-line))))))

(defn run [plugin]
  (default! plugin)
  (process-getmanifest! (read *in*) plugin *out*)
  (process-init! (read *in*) plugin *out*)
  (add-rpcmethod-to-plugin! :setconfig setconfig! plugin)

  (let [a (agent nil)]
    (loop [req (read *in*)]
      (when req
        (process a plugin req)
        (recur (read *in*))))))
