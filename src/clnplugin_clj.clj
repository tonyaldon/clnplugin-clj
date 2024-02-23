(ns clnplugin-clj
  "Core Lightning plugin library for Clojure."
  (:require [clojure.data.json :as json]))

(defn gm-option
  "Check option and return it in a format understable by lightningd.

  - TYPE and DESCRIPTION are mandatory.
  - TYPE can only be:
    - \"string\": a string
    - \"int\": parsed as a signed integer (64-bit)
    - \"bool\": a boolean
    - \"flag\": no-arg flag option.  Presented as true if config specifies it.
  - If TYPE is string or int, MULTI can set to true which indicates
    that the option can be specified multiple times.  These will always be represented
    in the init request as a (possibly empty) JSON array.

  If TYPE is not specied, set it to \"string\".

  If DESCRIPTION is not specied, set it to \"\"."
  [[kw-name {:keys [type description default multi] :as option}]]
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
                     {:kw-name kw-name :option option}))))]
    (merge -name -type -description -default -multi)))

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

(defn getmanifest!
  "Store getmanifest params of REQ in PLUGIN.

  REQ is meant to be the \"getmanifest\" request receive from lightningd."
  [plugin req]
  (swap! plugin #(update % :getmanifest
                         (constantly (select-keys req [:params])))))

(defn getmanifest-resp
  "Return the response to the getmanifest REQ.

  PLUGIN can contain any key; only the keys :options, :rpcmethods,
  :subscriptions, :hooks, :featurebits, :notifications, :custommessages,
  :nonnumericids and :dynamic will retained in the response to be
  sent to lightningd.

  Note that only :options and :rpcmethods are mandatory.

  See clnplugin-clj/gm-rpcmethods, clnplugin-clj/gm-options,
  and clnplugin-clj/default! ."
  [plugin req]
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
