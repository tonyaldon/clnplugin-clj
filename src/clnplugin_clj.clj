(ns clnplugin-clj
  "Core Lightning plugin library for Clojure."
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str])
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
  [[kw-name {:keys [type description default multi dynamic deprecated]}]]
  ;; Don't check options.  Raising an exception here is useless
  ;; because lightningd won't let us log it or will ignore any json
  ;; response (with an `error` field) to the getmanifest request.
  (let [name {:name (name kw-name)}
        type (if (nil? type) {:type "string"} {:type type})
        description {:description (or description "")}
        default (when default {:default default})
        multi (when multi {:multi true})
        dynamic (and dynamic {:dynamic true})
        deprecated (and deprecated {:deprecated true})]
    (merge name type description default multi dynamic deprecated)))

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
  and clnplugin-clj/set-defaults! ."
  [req plugin]
  (let [p @plugin]
    {:jsonrpc "2.0"
     :id (:id req)
     :result {:options (gm-options (:options p))
              :rpcmethods (gm-rpcmethods (:rpcmethods p))
              :dynamic (:dynamic p)}}))

(defn set-defaults!
  "Set default values for :dynamic, :options and :rpcmethods keys if omitted."
  [plugin]
  (swap! plugin #(merge {:options {} :rpcmethods {} :dynamic true}
                        %)))

(defn add-request!
  "Store params of REQ in PLUGIN.

  Use this to store params received from CLN in \"getmanifest\" and
  \"init\" requests.

  For instance, after using that function for \"init\" request,
  we can get lightningd configuration like this (`plugin` being
  the atom holding the state of our plugin):

      (get-in plugin [:init :configuration])"
  [req plugin]
  (swap! plugin assoc (keyword (:method req)) (:params req)))

(defn process-getmanifest!
  "Process REQ being the \"getmanifest\" request received from lightningd.

  We store that REQ in PLUGIN.  See clnplugin-clj/add-request!.
  We send the response produced by clnplugin-clj/gm-resp."
  [req plugin]
  (let [out (:_out @plugin)]
    (add-request! req plugin)
    (json/write (gm-resp req plugin) out :escape-slash false)
    (. out (append "\n\n")) ;; required by lightningd
    (. out (flush))))

(defn set-option!
  "Set KW-OPT to VALUE in PLUGIN.

  If AT-INIT? is false and KW-OPT is not a dynamic option,
  raise an exception.

  Plugin options must not be set by plugin code but by

  - clnplugin-clj/process-init! when the plugin processes lightningd
    \"init\" request, or by,
  - clnplugin-clj/setconfig! when the user dynamically sets a
    dynamic option with `setconfig` lightningd JSON RPC command.

  See clnplugin-clj/gm-option"
  ([[kw-opt value] plugin]
   (set-option! [kw-opt value] plugin false))
  ([[kw-opt value] plugin at-init?]
   (if (contains? (:options @plugin) kw-opt)
     (let [is-dynamic? (get-in @plugin [:options kw-opt :dynamic])]
       (if (or at-init? is-dynamic?)
         (swap! plugin assoc-in [:options kw-opt :value] value)
         (throw
          (let [msg (format "Cannot set '%s' option which is not dynamic.  Add ':dynamic true' to its declaration." kw-opt)]
            (ex-info msg {:error {:code -32600 :message msg}})))))
     (throw
      (let [msg (format "Cannot set '%s' option which has not been declared to lightningd" kw-opt)]
        (ex-info msg {:error {:code -32600 :message msg}}))))))

(defn set-options-at-init!
  "Set OPTIONS in PLUGIN.

  This is meant to be used by clnplugin-clj/process-init! when
  we process lightningd \"init\" request."
  [options plugin]
  (when-not (empty? options)
    (doseq [opt (seq options)]
      (set-option! opt plugin :at-init))))

(defn stacktrace
  "Return EXCEPTION's stacktrace as a string."
  [exception]
  (let [sw (new java.io.StringWriter)
        pw (new java.io.PrintWriter sw)]
    (.printStackTrace exception pw)
    (str/replace (str sw) "\t" "  ")))

(defn process-init!
  "..."
  [req plugin]
  (let [dir (get-in req [:params :configuration :lightning-dir])
        rpc-file (get-in req [:params :configuration :rpc-file])
        socket-file (str (clojure.java.io/file dir rpc-file))
        options (get-in req [:params :options])
        out (:_out @plugin)
        _ (swap! plugin assoc-in [:socket-file] socket-file)
        _ (set-options-at-init! options plugin)
        _ (add-request! req plugin)
        ok-or-disable
        (if-let [init-fn (:init-fn @plugin)]
          (if (fn? init-fn)
            (try
              (let [result (init-fn req plugin)]
                (if (and (map? result) (contains? result :disable))
                  result
                  {}))
              (catch Exception e
                {:disable (stacktrace e)}))
            (let [msg (format ":init-fn must be a function not '%s' which is an instance of '%s'"
                              init-fn (class init-fn))]
              {:disable msg}))
          {})
        resp (assoc {:jsonrpc "2.0" :id (:id req)} :result ok-or-disable)]
    (json/write resp out :escape-slash false)
    (. out (append "\n\n")) ;; required by lightningd
    (. out (flush))))

(defn setconfig!
  "Set in PLUGIN the dynamic option specified in PARAMS to its new value.

  This function is meant to be used to process \"setconfig\"
  requests sent by lightningd.

  When we declare to lightningd that \"foo-opt\" is a dynamic
  option (during the getmanifest round, see clnplugin-clj/gm-option),
  that means that the user can use lightningd's JSON RPC command
  \"setconfig\" to set that option without restarting neither lightningd
  nor the plugin.

  Let's unfold this.

  When the user wants to set dynamically \"foo-opt\" to \"foo-value\"
  using `lightning-cli`, he runs the following command

      lightning-cli setconfig foo-opt foo-value

  which triggered lightningd to send us the following \"setconfig\"
  request:

      {\"jsonrpc\": \"2.0\",
       \"id\": \"cli:setconfig#49479/cln:setconfig#66752\",
       \"method\": \"setconfig\",
       \"params\": {\"config\": \"foo-opt\",
                  \"val\": \"foo-value\"}}

  Finally, we handle that request with clnplugin-clj/setconfig! which

  - sets the option :foo-opt to \"foo-value\" in :options map
    of PLUGIN and,
  - returns an empty map if everything is OK."
  [params plugin]
  (let [kw-opt (keyword (:config params))
        value (:val params)]
    (set-option! [kw-opt value] plugin)
    {}))

(defn add-rpcmethod!
  "Add METHOD to :rpcmethods of PLUGIN with :fn being FN.

  The RPC methods should not be added to the plugin state with
  that function but set when the plugin is first defined.

  Specifically, anything that must be declared to lightningd
  must be in plugin's state before the getmanifest round.

  See clnplugin-clj/run, clnplugin-clj/gm-resp and
  clnplugin-clj/setconfig!"
  [method fn plugin]
  (swap! plugin assoc-in [:rpcmethods method] {:fn fn}))

(defn notif
  "Return a METHOD notification with PARAMS to be send to lightningd.

  As this is a notification, the returned map contains no :id key."
  [method params]
  {:jsonrpc "2.0" :method method :params params})

(defn write
  "Write to OUT the responses and notifications in RESPS collection.

  Elements in RESPS are responses with :id key

      {:jsonrpc \"2.0\"
       :id \"some-id\"
       :result {:foo \"bar\"}}

      or

      {:jsonrpc \"2.0\"
       :id \"some-id\"
       :error {:code -32600
               :message \"Something wrong happened\"}}

  or they are notifications without :id key like this

      {:jsonrpc \"2.0\"
       :method \"log\"
       :params {:level \"debug\"
                :message msg}}

  That function is meant to be an action-fn we send to
  an agent along with RESPS and OUT.  We use an agent to
  synchronize writes to OUT.  Specifically, the agent
  is the value of :_resps key of the plugin.

  Note that to synchronize these writes, we don't need
  to use the state of the agent which is passed as first
  argument of any action-fn.  So the first argument of
  clnplugin-clj/write is ignored.

  See clnplugin-clj/log and clnplugin-clj/process"
  [_ resps out]
  (doseq [r resps]
    (json/write r out :escape-slash false)
    (. out (append "\n\n")) ;; required by lightningd
    (. out (flush))))

(defn log
  "Send a \"log\" notification to lightningd with level LEVEL.

  If LEVEL is not specified, it is set to \"info\".  As per
  common/status_levels.c file, log levels can be:

  - \"io\",
  - \"debug\",
  - \"info\",
  - \"unusual\" (also \"warn\"),
  - \"broken\" (also \"error\").

  MESSAGE is a string.  If it contains multiple lines, it is split
  at newline separation and several \"log\" notifications are sent
  instead of one.  This is useful for sending stacktraces when
  our plugin stops working correctly and throws exceptions.

  See clnplugin-clj/stacktrace"
  ([plugin message]
   (log plugin message "info"))
  ([plugin message level]
   (let [notifs (map #(notif "log" {:level level :message %})
                     (str/split-lines message))]
     (send (:_resps @plugin) write notifs (:_out @plugin)))
   nil))

(defn process
  "..."
  [req plugin]
  (go
    (let [method (keyword (:method req))
          method-fn (get-in (:rpcmethods @plugin) [method :fn])
          result-or-error
          (try {:result (method-fn (:params req) plugin)}
               (catch clojure.lang.ExceptionInfo e
                 (let [msg (format "Error while processing '%s'" req)
                       error (merge {:code -32600 :message msg}
                                    (:error (ex-data e)))]
                   (log plugin msg "debug")
                   (log plugin (format "%s" error) "debug")
                   {:error error}))
               (catch Exception e
                 (let [msg (format "Error while processing '%s'" req)]
                   (log plugin msg "debug")
                   (log plugin (stacktrace e) "debug")
                   {:error {:code -32600
                            :message msg
                            :stacktrace (stacktrace e)}})))
          resp (merge {:jsonrpc "2.0" :id (:id req)}
                      result-or-error)
          out (:_out @plugin)
          resps (:_resps @plugin)]
      (send resps write [resp] out))))

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
  (set-defaults! plugin)
  (swap! plugin assoc :_out *out*)
  (process-getmanifest! (read *in*) plugin)
  (process-init! (read *in*) plugin)
  (add-rpcmethod! :setconfig setconfig! plugin)
  (swap! plugin assoc :_resps (agent nil))
  (loop [req (read *in*)]
    (when req
      (process req plugin)
      (recur (read *in*)))))
