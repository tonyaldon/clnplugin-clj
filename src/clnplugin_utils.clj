(in-ns 'clnplugin-clj)

(defn dev-set-rpcmethod
  "..."
  [plugin method fn]
  (swap! plugin assoc-in [:rpcmethods method :fn] fn))

(defn dev-set-subscription
  "..."
  [plugin topic fn]
  (swap! plugin assoc-in [:subscriptions topic :fn] fn))

(defn dev-set-hook
  "..."
  [plugin hook fn]
  (swap! plugin assoc-in [:hooks hook :fn] fn))

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
