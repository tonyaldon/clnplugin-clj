(in-ns 'clnplugin-clj)

(defn get-option
  "Return value of KW-OPT in PLUGIN if any.

  If value has not been set, return the default value if any.
  If no value, no default or if KW-OPT is not defined in PLUGIN's
  options, return nil."
  [plugin kw-opt]
  (or (get-in @plugin [:options kw-opt :value])
      (get-in @plugin [:options kw-opt :default])))

(defn params->map
  "Return PARAMS as a map binding PARAMS's elements to keys in KEYS.

  If PARAMS is a map, return only entries in PARAMS whose key is
  in KEYS (as clojure.core/select-keys).

  If PARAMS is a vector, return a map with the KEYS mapped to the
  corresponding PARAMS values (as clojure.core/zipmap).

  For instance calling

      (params->map [:foo :bar :baz]
                   {:foo \"foo-value\" :bar \"bar-value\"})

  gives us

      {:foo \"foo-value\" :bar \"bar-value\"}

  and calling

      (params->map [:foo :bar :baz]
                   [\"foo-value\" \"bar-value\"])

  gives us:

      {:foo \"foo-value\" :bar \"bar-value\"}

  This function may be useful for destructuring params argument
  of the :fn functions used in :rpcmethods (and maybe :subscriptions
  too) of the plugin we define and pass as argument of `run` function.

  For instance, if we declare a method \"foo\" to lightningd which
  takes two parameters \"bar\" and \"baz\" we can use `params->map`
  to destructure params argument of foo's :fn function like this:

      {:rpcmethods
       {:foo
        {:fn (fn [params req plugin]
               (let [{:keys [bar baz]}
                     (plugin/params->map [:bar :baz] params)]
                 ,,,))}
        ,,,}}

  Indeed, if a client sends to lightningd a \"foo\" JSON RPC request
  with the \"params\" field being a vector and our plugin declared
  \"foo\" JSON RPC method to lightningd, lightningd will forward to
  us (the plugin) the request with \"params\" unchanged and thus
  being a vector.

  This happens for instance with lightning-cli client run like this

      lightning-cli foo bar-value baz-value

  that sends a JSON RPC request to lightningd which is forwarded to us
  (the plugin) as this JSON RPC request:

      {\"jsonrpc\": \"2.0\",
       \"id\": \"cli:foo#49479/cln:foo#66752\",
       \"method\": \"foo\",
       \"params\": [\"bar-value\" \"baz-value\"]}

  Notice that the user could send a similar request (the request is
  different but the JSON RPC response must be the same) using -k
  flag of lightning-cli client running this command

      lightning-cli -k foo bar=bar-value baz=baz-value

  that sends a JSON RPC request to lightningd which is forwarded to us
  (the plugin) as this JSON RPC request with \"params\" being an object:

      {\"jsonrpc\": \"2.0\",
       \"id\": \"cli:foo#49479/cln:foo#66752\",
       \"method\": \"foo\",
       \"params\": {\"bar\": \"bar-value\",
                  \"baz\": \"baz-value\"}}"
  [keys params]
  (or (and (map? params)
           (select-keys params keys))
      (zipmap keys params)))

(defn dev-set-rpcmethod
  "Set :fn key of METHOD in PLUGIN's :rpcmethods with FN.

  This is an helper function for developing the plugin interactively
  when you are connected to the plugin's process (started by lightningd)
  using a socket REPL.  DON'T USE IT IN PLUGIN's CODE.

  For instance, if you declared :foo method in PLUGIN's :rpcmethods
  map and your are in the namespace where plugin is defined, you
  can set its :fn function to always return {:bar \"baz\"} like this:

      (dev-set-rpcmethod plugin :foo
        (fn [params req plugin] {:bar \"baz\"}))"
  [plugin method fn]
  (swap! plugin assoc-in [:rpcmethods method :fn] fn))

(defn dev-set-subscription
  "Set :fn key of TOPIC in PLUGIN's :subscriptions with FN.

  This is an helper function for developing the plugin interactively
  when you are connected to the plugin's process (started by lightningd)
  using a socket REPL.  DON'T USE IT IN PLUGIN's CODE.

  For instance, if you declared :invoice_creation notification topic
  in PLUGIN's :subscriptions map and your are in the namespace where
  plugin is defined, you can set its :fn function to log the
  invoice_creation notification you receive each time an invoice is
  created like this

      (dev-set-subscription plugin :peer_connected
        (fn [params req plugin]
          (plugin/log (format \"%s\" req) plugin)))"
  [plugin topic fn]
  (swap! plugin assoc-in [:subscriptions topic :fn] fn))

(defn dev-set-hook
  "Set :fn key of HOOK in PLUGIN's :hooks with FN.

  This is an helper function for developing the plugin interactively
  when you are connected to the plugin's process (started by lightningd)
  using a socket REPL.  DON'T USE IT IN PLUGIN's CODE.

  For instance, if you declared :peer_connected hook in PLUGIN's :hooks
  map and your are in the namespace where plugin is defined, you can
  set its :fn function to log the peer id of the node we are going to
  connect to like this:

      (dev-set-hook plugin :peer_connected
        (fn [params req plugin]
          (plugin/log (format \"peer-id: %s\" (get-in params [:peer :id]))
                      plugin)
          {:result \"continue\"}))"
  [plugin hook fn]
  (swap! plugin assoc-in [:hooks hook :fn] fn))
