(ns clnplugin-clj
  "Core Lightning plugin library for Clojure."
  (:refer-clojure :exclude [read])
  (:require [clojure.string :as str])
  (:require [clojure.data.json :as json])
  (:require [clojure.core.async :refer [go >!! <!! chan thread]]))

(defn gm-option
  "Check option and return it in a format understable by lightningd.

  For instance, given [:foo {:type \"int\" :default -1}] this function
  returns:

      {:name \"foo\" :type \"int\" :description \"\" :default -1}

  - TYPE and DESCRIPTION are mandatory.
  - TYPE can only be:
    - \"string\": a string
    - \"int\": parsed as a signed integer (64-bit)
    - \"bool\": a boolean
    - \"flag\": no-arg flag option.  Presented as true if config specifies it.
  - If TYPE is string or int, MULTI can be set to true which indicates
    that the option can be specified multiple times.  These will always
    be represented in the init request as a (possibly empty) JSON array.

  If TYPE is not specified, set it to \"string\".

  If DESCRIPTION is not specified, set it to \"\".

  If DYNAMIC is true, KW-NAME can be set dynamically with `setconfig`
  lightningd command.

  If DEPRECATED is true and the user sets lightningd option
  `allow-deprecated-apis` to false, KW-NAME option is disabled
  by lightningd and must not be used by the plugin.

  See `gm-options`."
  [[kw-name {:keys [type description default multi dynamic deprecated]}]]
  ;; Don't check options.  Raising an exception here is useless
  ;; because lightningd won't let us log it or will ignore any json
  ;; response (with an error field) to the getmanifest request.
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

  See `gm-option` and `process-getmanifest!`."
  [options]
  (mapv gm-option (seq options)))

(defn gm-rpcmethods
  "Return the vector of RPC methods meant to be used in the getmanifest response.

  RPCMETHODS is a list ([kw-1 map-1] [kw-2 map-2] ...) obtained from
  :rpcmethods map of the plugin's definition.  kw-1 defines the name
  (as keyword) of a RPC method and map-1 the information about that
  method.  The same goes for [kw-2 map-2], ...

  map-1 must contains a :fn key:

  - whose value is a function taking 3 arguments [params req plugin]:

    - params: value of \"params\" field of kw-1 request we receive from
              lightningd,
    - req:    kw-1 request we receive from lightingd.  (:params req)
              is equal to the first argument params.  (:id req) gives
              us the id of the request.  Needed for RPC methods that
              would like to use `notify-message` and `notify-progress`
              functions.
    - plugin: the plugin (which is an atom) that we passed to `run`
              function to run the plugin.  It contains the current
              state of the plugin.

  - when the plugin receives a request for kw-1 method, `clnplugin-clj`
    calls its :fn function:

    1) if no exception thrown, `clnplugin-clj` replies to lightningd with
       a 'result' field in the response set to the value returned by :fn,
    2) if an exception is thrown, `clnplugin-clj` replies to lightningd with
       an \"error\" field in the response set with an error \"code\", a
       \"message\", the \"exception\" depending on who thrown it and maybe
       additional informations.

       What does this means?

       1) if :fn is wrongly written `clnplugin-clj` will catch any
          `Throwable` and report it to the caller (via lightningd) in the
          form of a JSON RPC error response,
       2) if for some request you want to reply to lightningd with a
          JSON RPC error response, you just have to throw a `Throwable`.
          The best way to do this is to throw a `clojure.lang.ExceptionInfo`
          with an :error key (containing :code and :message keys) in the
          map passed to `clojure.core/ex-info` like this:

              (throw
               (ex-info \"some message\"
                        {:error {:code -100 :message \"some message\"}}))

          Any additional information in :error map will retained in the
          JSON RPC error response.  If :error doesn't contain :code or
          :message keys, they will be created for you, :code defaulting
          to -32603.

    Note that if :fn returned value contains non JSON writable objects
    (but doesn't throw an exception), `clnplugin-clj` transforms the response
    to lighningd into a JSON RPC error with the following fields: \"code\",
    \"message\", \"exception\", \"request\" and \"response\".  This behavior
    is interesting for three reasons:

    - when writing :fn we can mistakenly put non JSON writable objects
      and I prefer to get noticed of this with a graceful JSON RPC error
      than by the JSON writer library with an exception like this:

          Execution error at clojure.data.json/default-write-fn (json.clj:783).
          Don't know how to write JSON of class clojure.lang.Atom

    - when writing our plugin, we can inspect the state of our plugin
      by returning any desired object in :fn returned value,
    - `clnplugin-clj` doesn't silently convert non JSON writable objects
      into strings in your normal JSON RPC response which may cause
      lightningd or other plugins receiving that JSON to throw an error
      because they don't understand those string representations of Clojure
      objects. I prefer to get the error reported by `clnplugin-clj`
      eagerly.  A little drawback of doing this is that when a method only
      does side effects, we have to put an empty object like {} or nil at
      the end to be returned in the JSON response to lightningd.

  Let's take an example in which we define a plugin that declares to
  lightningd a method 'foo' that always returns {\"bar\": \"baz\"} JSON
  object when called.

  We can define it like this

      (def plugin
        (atom {:rpcmethods
               {:foo {:usage \"how to use 'foo'\"
                      :description \"some description\"
                      :fn (fn [params req plugin]
                            {:bar \"baz\"})}}}))

  and run it like this:

      (run plugin)

  At some point `gm-rpcmethods` will receive the following list as argument

      ([:foo {:usage \"how to use 'foo'\"
              :description \"some description\"
              :fn #object[...]}])

  and will returns

      [{:name \"foo\"
        :usage \"how to use 'foo'\"
        :description \"some description\"}]

  which lightningd understands.

  Note that the \"rpcmethods\" field is required in the getmanifest response
  we send back to lightningd:

  - if no method to define, \"rpcmethods\" must be an empty vector, so
    `gm-rpcmethods` returns [] if RPCMETHODS is empty,
  - if methods are supplied, they must contain \"name\", \"usage\" and
    \"description\" fields.  If we don't specify them in map of [kw map]
    elements of RPCMETHODS, they will be set to the empty string \"\".

  See `gm-resp`, `process-getmanifest!` and `process`."
  [rpcmethods]
  (let [f (fn [[kw-name method]]
            (let [method-fn (:fn method)]
              (cond
                (nil? method-fn)
                (throw (ex-info (format ":fn is not defined for '%s' RPC method"
                                        kw-name) {}))
                (not (fn? method-fn))
                (throw (ex-info (format "Error in '%s' RPC method definition.  :fn must be a function not '%s' which is an instance of '%s'"
                                        kw-name method-fn (class method-fn)) {}))))
            (merge {:name (name kw-name)
                    :usage (get method :usage "")
                    :description (get method :description "")}
                   (when-let [l-desc (:long_description method)]
                     {:long_description l-desc})
                   (when (:deprecated method) {:deprecated true})))]
    (mapv f (seq rpcmethods))))

(defn gm-notifications
  "Return the vector of notifications meant to be used in the getmanifest response.

  Check that it doesn't contain \"log\", \"message\" and \"progress\" specific
  notification topics already defined by lightningd.

  If you want to send these specific notifications use

  - `clnplugin-clj/log`,
  - `clnplugin-clj/notify-message` or
  - `clnplugin-clj/notify-progress`

  functions without adding the notification topics \"log\", \"message\" and
  \"progress\" to :notifications vector of the plugin definition.
  "
  [notifications]
  (when notifications
    (let [f (fn [topic]
              (cond
                (= topic "log") (throw (ex-info "Remove 'log' from :notifications vector.  This is a specific notification that expects a specific params field in the JSON RPC notification.  It is used to log messages in lightningd log file.  To do this use clnplugin-clj/log function." {}))
                (= topic "message") (throw (ex-info "Remove 'message' from :notifications vector.  This is a specific notification that expects a specific params field in the JSON RPC notification.  It is used to tell lightningd that the response to a JSON RPC request from lightningd is being processed.  To send 'message' notifications use clnplugin-clj/notif-message." {}))
                (= topic "progress") (throw (ex-info "Remove 'progress' from :notifications vector.  This is a specific notification that expects a specific params field in the JSON RPC notification.  It is used to tell lightningd that the response to a JSON RPC request from lightningd is being processed and to inform about its progress before replying with the actual response.  To send 'progress' notifications use clnplugin-clj/notif-progress." {}))
                true {:method topic}))]
      (mapv f notifications))))

(defn gm-resp
  "Return the response to the getmanifest REQ.

  PLUGIN can contain any key you want, but only the following
  keys

  - :options,
  - :rpcmethods,
  - :subscriptions,
  - :hooks,
  - :notifications,
  - :dynamic,
  - :featurebits,
  - :custommessages.

  will retained in the response to be sent to lightningd.

  Note that only :options and :rpcmethods are mandatory.  If you don't
  specify them when you define your plugin, default empty values will
  be set by `clnplugin-clj/set-defaults!` in `clnplugin-clj/run` function.

  See `clnplugin-clj/gm-rpcmethods`, `clnplugin-clj/gm-options`,
  and `clnplugin-clj/set-defaults!`."
  [req plugin]
  (let [p @plugin]
    {:jsonrpc "2.0"
     :id (:id req)
     :result
     (merge {:options (gm-options (:options p))
             :rpcmethods (gm-rpcmethods (:rpcmethods p))
             :dynamic (:dynamic p)}
            (when-let [notifications (gm-notifications (:notifications p))]
              {:notifications notifications}))}))

(defn set-defaults!
  "Set default values for :dynamic, :options and :rpcmethods keys if omitted.

  In particular, plugins are dynamic by default if not otherwise specified."
  [plugin]
  (swap! plugin #(merge {:options {} :rpcmethods {} :dynamic true} %)))

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

(defn exception
  "..."
  [e]
  (let [sw (new java.io.StringWriter)]
    (print-method e sw)
    (str sw)))

(defn get-option
  "..."
  [plugin kw-opt]
  (or (get-in @plugin [:options kw-opt :value])
      (get-in @plugin [:options kw-opt :default])))

(defn convert-opt-value
  "..."
  [value type]
  ;; at the init round, value have the correct type specified
  ;; by the plugin.  So we return the value as is.  But,
  ;; if set dynamically with `setconfig` lightningd command,
  ;; lightningd send us any value as a string, so we need
  ;; to convert it to the type specified by the plugin.
  (if-not (or (string? value) (nil? value))
    value
    (cond
      (or (= type "string") (nil? type)) value
      (= type "int") (Long/parseLong value)
      (= type "bool") (Boolean/parseBoolean value)
      ;; when `setconfig` has just the config argument set,
      ;; this indicate to turn on the flag.  In that case,
      ;; value is nil.
      ;;
      ;; Note: as far as I understand, there's no way to turn off
      ;; a flag dynamically as of CLN 24.02
      (= type "flag") (if (nil? value)
                        true
                        (Boolean/parseBoolean value)))))

(defn set-option!
  "Set KW-OPT's :value to VALUE in PLUGIN's :options.

  :check-opt function:

  1) Before setting the option's value, call :check-opt function
     with VALUE and PLUGIN as argument.  If VALUE is not valid for
     KW-OPT, :check-opt must thrown an exception with a message.
     For instance, if KW-OPT is :foo, this can be done like this:

         (throw (ex-info \"Wrong option 'foo'\" {}))

  2) If an excetpion is thrown, the plugin will be disable during
     the init round.  See clnplugin-clj/process-init!
  3) Side effects, can be done in :check-opt.
  4) :check-opt is specific to each KW-OPT.

  If AT-INIT? is false and KW-OPT is not a dynamic option,
  throw an exception.

  Plugin options must not be set by plugin code but by

  - clnplugin-clj/process-init! when the plugin processes lightningd
    \"init\" request, or by,
  - clnplugin-clj/setconfig! when the user dynamically sets a
    dynamic option with `setconfig` lightningd JSON RPC command.

  See clnplugin-clj/gm-option"
  ([[kw-opt value] plugin]
   (set-option! [kw-opt value] plugin false))
  ([[kw-opt value] plugin at-init?]
   (let [value (convert-opt-value value (get-in @plugin [:options kw-opt :type]))
         dynamic? (get-in @plugin [:options kw-opt :dynamic])
         check-opt (get-in @plugin [:options kw-opt :check-opt])
         msg (cond
               (not (contains? (:options @plugin) kw-opt))
               (format "Cannot set '%s' option which has not been declared to lightningd" kw-opt)
               (and (not at-init?) (not dynamic?))
               (format "Cannot set '%s' option which is not dynamic.  Add ':dynamic true' to its declaration." kw-opt)
               (nil? check-opt)
               nil
               (fn? check-opt)
               (try
                 (check-opt value plugin)
                 nil
                 (catch clojure.lang.ExceptionInfo e (ex-message e))
                 (catch Exception e
                   (format ":check-opt of '%s' option thrown the following exception when called with '%s' value: %s"
                           kw-opt value (exception e))))
               true
               (format ":check-opt of '%s' option must be a function not '%s' which is an instance of '%s'"
                       kw-opt check-opt (class check-opt)))]
     (if (nil? msg)
       (swap! plugin assoc-in [:options kw-opt :value] value)
       (if at-init?
         (throw (ex-info msg {:disable msg}))
         (throw (ex-info msg {:error {:code -32602 :message msg}})))))))


(defn set-options-at-init!
  "Set OPTIONS in PLUGIN.

  This is meant to be used by clnplugin-clj/process-init! when
  we process lightningd \"init\" request."
  [options plugin]
  (when-not (empty? options)
    (doseq [opt (seq options)]
      (set-option! opt plugin :at-init))))

(defn process-init!
  "..."
  [req plugin]
  (let [dir (get-in req [:params :configuration :lightning-dir])
        rpc-file (get-in req [:params :configuration :rpc-file])
        socket-file (str (clojure.java.io/file dir rpc-file))
        options (get-in req [:params :options])
        out (:_out @plugin)
        _ (swap! plugin assoc-in [:socket-file] socket-file)
        _ (add-request! req plugin)
        opts-disable (try
                       (set-options-at-init! options plugin)
                       (catch clojure.lang.ExceptionInfo e (ex-data e)))
        init-fn (:init-fn @plugin)
        ok-or-disable
        (cond
          (and (map? opts-disable) (contains? opts-disable :disable)) opts-disable
          (nil? init-fn) {}
          (fn? init-fn) (try
                          (init-fn req plugin)
                          {}
                          (catch clojure.lang.ExceptionInfo e {:disable (ex-message e)})
                          (catch Exception e {:disable (exception e)}))
          true {:disable (format ":init-fn must be a function not '%s' which is an instance of '%s'"
                                 init-fn (class init-fn))})
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
  [params req plugin]
  (let [kw-opt (keyword (:config params))
        value (:val params)]
    (set-option! [kw-opt value] plugin)
    {}))

(defn notif
  "Return a METHOD notification with PARAMS to be send to lightningd.

  As this is a notification, the returned map contains no :id key."
  [method params]
  {:jsonrpc "2.0" :method method :params params})

(defn json-default-write
  "Function to handle types which are unknown to data.json.

  If the user gives us an object to write back to lightningd
  that data.json doesn't handle, we 'stringify' it by wrapping
  it in a (format \"%s\" ...) call.

  This function is meant to be used as value of the option
  :default-write-fn of json/write function.

  See clnplugin-clj/process-getmanifest!, clnplugin-clj/process-init!
  and clnplugin-clj/write"
  [x out options]
  (let [sw (new java.io.StringWriter)]
    (print-method x sw)
    ;; json/write-string is private to clojure.data.json library!
    (#'json/write-string (str sw) out options)))

(defn- log-
  "..."
  [msg out]
  (doseq [m (str/split-lines msg)]
    (let [notif (notif "log" {:level "debug" :message m})]
      (json/write notif out
                  :escape-slash false
                  :default-write-fn json-default-write)
      (. out (append "\n\n")) ;; required by lightningd
      (. out (flush)))))

(defn write-resp
  "..."
  [req resp out]
  (let [r (try
            (json/write-str resp :escape-slash false)
            (catch Exception e
              (let [msg (format "Error while processing '%s', some objects in the response are not JSON writable" req)
                    e-str (exception e)
                    error {:code -32603
                           :message msg
                           :exception e-str
                           :request req
                           :response resp}
                    new-resp (assoc (dissoc resp :error :result)
                                    :error error) ]
                (log- msg out)
                (log- e-str out)
                (json/write-str new-resp
                                :escape-slash false
                                :default-write-fn json-default-write))))]
    (. out (append r))
    (. out (append "\n\n")) ;; required by lightningd
    (. out (flush))))

(defn write-notif
  "..."
  [notif out]
  (let [r (try
            (json/write-str notif :escape-slash false)
            (catch Exception e
              (let [msg (format "Error while sending notification '%s', some objects are not JSON writable" notif)]
                (log- msg out)
                (log- (exception e) out)
                nil)))]
    (when r
      (. out (append r))
      (. out (append "\n\n")) ;; required by lightningd
      (. out (flush)))))

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
                :message \"Some message\"}}

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
  (doseq [[req resp] resps]
    (if req
      (write-resp req resp out)
      (write-notif resp out))))

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
  instead of one.  This is useful for sending exceptions when
  our plugin stops working correctly and throws exceptions.

  See clnplugin-clj/exception"
  ([message plugin]
   (log message "info" plugin))
  ([message level plugin]
   {:pre [(string? message)]}
   (let [notifs (map #(vector nil (notif "log" {:level level :message %}))
                     (str/split-lines message))]
     (send (:_resps @plugin) write notifs (:_out @plugin)))
   nil))

(defn notify
  "Send a TOPIC notification to lightningd with PARAMS params.

  Except for \"progress\" and \"message\" notifications, TOPIC
  must be declared to lightningd during the getmanifest round.
  If this is not the case, lightningd will ignore the notification
  (not forwarding it to the subscriber) and log something like this:

      Plugin attempted to send a notification to topic ...
      it hasn't declared in its manifest, not forwarding to subscribers.

  See plugin/gm-resp"
  ([topic params plugin]
   (send (:_resps @plugin) write [[nil (notif topic params)]] (:_out @plugin))
   nil))

(defn notify-message
  "..."
  ([message req plugin]
   (notify-message message "info" req plugin))
  ([message level req plugin]
   {:pre [(string? message)]}
   (notify "message" {:id (:id req) :message message :level level} plugin)))

(defn notify-progress
  "..."
  ([step total-steps req plugin]
   (notify-progress step total-steps nil nil req plugin))
  ([step total-steps stage total-stages req plugin]
   {:pre [(and (int? step) (int? total-steps) (< step total-steps))
          (or (nil? stage) (nil? total-stages)
              (and (int? stage) (int? total-stages) (< stage total-stages)))]}
   (let [params
         (merge {:id (:id req) :num step :total total-steps}
                (when (and stage total-stages)
                  {:stage {:num stage :total total-stages}}))]
     (notify "progress" params plugin))))

(defn process
  "Process REQ.

  Check if :method of REQ belong to :rpcmethods map of PLUGIN.
  If yes, try to apply its :fn function to :params of REQ and
  PLUGIN:

  1) if no exception is thrown, send back a response to lightningd
     with :result being the returned value of applying :fn to
     :params and PLUGIN,
  2) if an exception is thrown, use it as the value of :error
     key of the response to lightningd.  In that case, we log
     the exception.  See clnplugin-clj/exception and clnplugin-clj/log

  All the processing is done in a go block.

  Writes to :_out of PLUGIN are synchronized with :_resps agent and
  clnplugin-clj/write action function."
  [req plugin]
  (let [req-id (:id req)
        method (keyword (:method req))
        method-fn (get-in (:rpcmethods @plugin) [method :fn])
        msg (format "Error while processing '%s'" req)
        jsonrpc {:jsonrpc "2.0" :id req-id}]
    (try
      ;; nil because nothing to log
      [nil (merge jsonrpc {:result (method-fn (:params req) req plugin)})]
      (catch clojure.lang.ExceptionInfo e
        (let [error (merge {:code -32603 :message msg} (:error (ex-data e)))]
          [[msg (format "%s" error)] (merge jsonrpc {:error error})]))
      (catch Throwable e
        [[msg (exception e)]
         (merge jsonrpc {:error {:code -32603 :message msg :exception (exception e)}})]))))

(defn read
  "Read a CLN JSON-RPC request from IN.

  CLN requests end with an empty line \"\\n\\n\"."
  [in]
  (binding [*in* in]
    (loop [req-acc "" line (read-line)]
      (cond
        (nil? line) nil
        ;; lightningd requests end with an empty line "\n\n".
        (empty? line) (try
                        (json/read-str req-acc :key-fn keyword)
                        (catch Exception e
                          (throw
                           (let [msg (format "Invalid token in json input: '%s'" req-acc)]
                             (ex-info msg {:error {:code -32700 :message msg}})))))
        true (let [next-line (read-line)]
               (recur (str req-acc line) next-line))))))

(defn max-parallel-reqs
  "..."
  [plugin]
  (let [mpr (:max-parallel-reqs @plugin)]
    ;; because clojure.core.async.impl.protocols/MAX-QUEUE-SIZE is 1024
    ;; if mpr not correctly provided we default to 512
    (max 1 (or (and (int? mpr) (min mpr 1023)) 512))))

(defn run [plugin]
  (let [in *in* out *out*
        max-parallel-reqs (max-parallel-reqs plugin)
        resps (agent nil) ;; to synchronize writes to out
        reqs (chan max-parallel-reqs) ;; to queue incoming requests from lightingd
        ;; to apply backpressure on incoming lightingd requests we restrict
        ;; the number of parallel requests being processed to max-parallel-reqs.
        ;; So we always have reqs-in-progress < max-parallel-reqs.
        reqs-in-progress (atom 0)]
    (set-defaults! plugin)
    (swap! plugin assoc :_out out) ;; for process-getmanifest!, process-init!, log and notify
    (swap! plugin assoc :_resps resps) ;; for log and notify
    (process-getmanifest! (read in) plugin)
    (process-init! (read in) plugin)
    (swap! plugin assoc-in [:rpcmethods :setconfig] {:fn setconfig!}) ;; For dynamic options
    (thread
      (loop [req (read in)]
        (when req
          (>!! reqs req)
          (recur (read in)))))
    (loop []
      (if (< @reqs-in-progress max-parallel-reqs)
        (let [req (<!! reqs)]
          (swap! reqs-in-progress inc)
          (go
            (try
              (let [[log-msgs resp] (process req plugin)]
                (doseq [msg log-msgs] (log msg "debug" plugin))
                (send resps write [[req resp]] out))
              (finally (swap! reqs-in-progress dec))))
          (recur))
        (recur)))))
