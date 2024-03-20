from pyln.testing.fixtures import *
from pyln.client import RpcError
import os
import time

def test_rpcmethods(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/rpcmethods")
    l1 = node_factory.get_node(options={"plugin": plugin})
    assert l1.rpc.call("foo-0") == {"bar": "baz"}
    assert l1.rpc.call("foo-1", {"baz-1": "baz-1"}) == {"bar-1": "baz-1"}
    assert l1.rpc.call("foo-2") == {"bar-2": "baz-2"}
    # foo-3 must be call before foo-4 because it sets a value in plugin atom
    # that we want to get with foo-4
    assert l1.rpc.call("foo-3") == {}
    assert l1.rpc.call("foo-4") == {"bar-4": "baz-3"}

    l2 = node_factory.get_node(options={'allow-deprecated-apis': False})
    l2.rpc.plugin_start(plugin=plugin)
    with pytest.raises(RpcError, match=r"Command \"foo-deprecated\" is deprecated"):
        l2.rpc.call("foo-deprecated")

    l3 = node_factory.get_node(options={'allow-deprecated-apis': True})
    l3.rpc.plugin_start(plugin=plugin)
    assert l3.rpc.call("foo-deprecated") == {"bar": "baz"}


def test_getmanifest(node_factory):
    l1 = node_factory.get_node()
    plugin = os.path.join(os.getcwd(), "pytest/plugins/getmanifest_fn_not_defined")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "pytest/plugins/getmanifest_fn_not_a_function")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "pytest/plugins/getmanifest_fn_symbol_not_a_function")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)


def test_init(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/init")
    l1 = node_factory.get_node(options={"plugin": plugin,
                                        "foo": "FOO",
                                        "bar": "BAR"})
    assert l1.rpc.call("get-x-set-at-init") == {"x": 1}
    assert l1.rpc.call("get-plugin-options-values") == {"foo": "FOO", "bar": "BAR"}
    l1.rpc.plugin_stop(plugin)
    l1.rpc.plugin_start(plugin, foo="foo-plugin-restarted", bar="bar-plugin-restarted")
    assert l1.rpc.call("get-x-set-at-init") == {"x": 1}
    assert l1.rpc.call("get-plugin-options-values") == {"foo": "foo-plugin-restarted",
                                                        "bar": "bar-plugin-restarted"}

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_check_opt")
    with pytest.raises(RpcError, match=r"Wrong option 'foo_1'"):
        l1.rpc.plugin_start(plugin, foo_1="foo-value")

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_check_opt_before_init_fn")
    with pytest.raises(RpcError, match=r"Wrong option 'foo_2'"):
        l1.rpc.plugin_start(plugin, foo_2="foo-value")

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_check_opt_not_a_function")
    with pytest.raises(RpcError, match=r":check-opt of.*foo_3.*option must be a function not.*[:a-vector \"is not a function\"].*which is an instance of.*class clojure.lang.PersistentVector"):
        l1.rpc.plugin_start(plugin, foo_3="foo-value")

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_check_opt_symbol_not_a_function")
    with pytest.raises(RpcError, match=r":check-opt of.*foo_.*option must be a function not .*some-symbol.*which is an instance of.*class clojure.lang.Symbol"):
        l1.rpc.plugin_start(plugin, foo_4="foo-value")

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_check_opt_execution_error")
    with pytest.raises(RpcError, match=r":check-opt of.*foo_5.* option thrown the following exception when called with.*foo-value.*value: #error.*:cause.*Divide by zero.*:type.*java.lang.ArithmeticException"):
        l1.rpc.plugin_start(plugin, foo_5="foo-value")

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_init_fn_not_a_function")
    with pytest.raises(RpcError, match=r":init-fn must be a function"):
        l1.rpc.plugin_start(plugin)

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_init_fn_execution_error")
    with pytest.raises(RpcError, match=r"#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"):
        l1.rpc.plugin_start(plugin)

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_init_fn")
    with pytest.raises(RpcError, match=r"disabled by user"):
        l1.rpc.plugin_start(plugin)


def test_options_deprecated(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/options_deprecated")

    l1 = node_factory.get_node(options={'allow-deprecated-apis': False})
    with pytest.raises(RpcError, match=r"deprecated option"):
        l1.rpc.plugin_start(plugin=plugin, foo_deprecated="foo-value")

    l2 = node_factory.get_node(options={'allow-deprecated-apis': True})
    l2.rpc.plugin_start(plugin=plugin, foo_deprecated="foo-value")
    listconfigs_resp = l2.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo_deprecated"]["value_str"] == "foo-value"
    assert l2.rpc.call("get-foo_deprecated-value") == {"foo_deprecated": "foo-value"}


def test_options_dynamic(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/options_dynamic")
    l1 = node_factory.get_node(options={"plugin": plugin})

    # foo-no-check-opt
    setconfig_resp = l1.rpc.setconfig(config="foo-no-check-opt", val="foo-value-0")
    assert setconfig_resp["config"]["value_str"] == "foo-value-0"
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-no-check-opt"]["value_str"] == "foo-value-0"
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-no-check-opt"}) == "foo-value-0"
    l1.stop()
    l1.start()
    assert listconfigs_resp["configs"]["foo-no-check-opt"]["value_str"] == "foo-value-0"
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-no-check-opt"}) == "foo-value-0"
    setconfig_resp = l1.rpc.setconfig(config="foo-no-check-opt", val="foo-value-1")
    assert setconfig_resp["config"]["value_str"] == "foo-value-1"
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-no-check-opt"]["value_str"] == "foo-value-1"
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-no-check-opt"}) == "foo-value-1"

    # options of type "int" and "bool"
    # we test them here because lightningd send setconfig
    # request with option values (json field "val") being string
    # and we want to be sure we convert them correctly in
    # plugin state
    #
    # type "int"
    # {"jsonrpc":"2.0","id":"pytest:setconfig#22/cln:setconfig#41","method":"setconfig","params":{"config":"foo-int-positive","val":"12"}}
    setconfig_resp = l1.rpc.setconfig(config="foo-int", val=12)
    assert setconfig_resp["config"]["value_int"] == 12
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-int"]["value_int"] == 12
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-int"}) == 12
    setconfig_resp = l1.rpc.setconfig(config="foo-int", val=-12)
    assert setconfig_resp["config"]["value_int"] == -12
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-int"]["value_int"] == -12
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-int"}) == -12

    # type "bool"
    # {"jsonrpc":"2.0","id":"pytest:setconfig#34/cln:setconfig#45","method":"setconfig","params":{"config":"foo-bool","val":"true"}}
    setconfig_resp = l1.rpc.setconfig(config="foo-bool", val=True)
    assert setconfig_resp["config"]["value_bool"] == True
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-bool"]["value_bool"] == True
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-bool"}) == True
    setconfig_resp = l1.rpc.setconfig(config="foo-bool", val=False)
    assert setconfig_resp["config"]["value_bool"] == False
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-bool"]["value_bool"] == False
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-bool"}) == False

    # type "flag"
    setconfig_resp = l1.rpc.setconfig(config="foo-flag")
    assert setconfig_resp["config"]["set"] == True
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-flag"]["set"] == True
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-flag"}) == True

    # foo-with-check-opt
    setconfig_resp = l1.rpc.setconfig(config="foo-with-check-opt", val=12)
    assert setconfig_resp["config"]["value_int"] == 12
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-with-check-opt"]["value_int"] == 12
    assert l1.rpc.call("get-opt-value", {"opt" : "foo-with-check-opt"}) == 12
    with pytest.raises(RpcError, match=r".*foo-with-check-opt.* option must be positive -1 type class java.lang.Long"):
        l1.rpc.setconfig(config="foo-with-check-opt", val=-1)
    assert l1.daemon.is_in_log(r"Error while processing.*:method.*setconfig.*:params.*foo-with-check-opt")
    assert l1.daemon.is_in_log(r".*foo-with-check-opt.* option must be positive -1 type class java.lang.Long")

    # foo-wrong-check-opt
    with pytest.raises(RpcError, match=r":check-opt of.*:foo-wrong-check-opt.*option must be a function not.*[:a-vector \"is not a function\"].*which is an instance of.*class clojure.lang.PersistentVector"):
        l1.rpc.setconfig(config="foo-wrong-check-opt", val="foo-value")
    assert l1.daemon.is_in_log(r"Error while processing.*:method.*setconfig.*:params.*foo-wrong-check-opt.*")
    assert l1.daemon.is_in_log(r":check-opt of.*:foo-wrong-check-opt.*option must be a function not.*[:a-vector \"is not a function\"].*which is an instance of.*class clojure.lang.PersistentVector")

    # foo-error-in-check-opt
    with pytest.raises(RpcError, match=r":check-opt of.*:foo-error-in-check-opt.* option thrown the following exception when called with.*foo-value.*value: #error.*:cause.*Divide by zero.*:type.*java.lang.ArithmeticException"):
        l1.rpc.setconfig(config="foo-error-in-check-opt", val="foo-value")
    assert l1.daemon.is_in_log(r"Error while processing.*setconfig.*:params.*foo-error-in-check-opt")
    assert l1.daemon.is_in_log(r":check-opt of.*:foo-error-in-check-opt.* option thrown the following exception when called with.*foo-value.*value: #error.*:cause.*Divide by zero.*:type.*java.lang.ArithmeticException")


def test_log(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/log")
    l1 = node_factory.get_node(options={"plugin": plugin})
    l1.rpc.call("log-info")
    assert l1.daemon.is_in_log(r"INFO.*logged by 'log-info'")
    l1.rpc.call("log-debug")
    assert l1.daemon.is_in_log(r"DEBUG.*logged by 'log-debug'")
    l1.rpc.call("log-multi-lines")
    assert l1.daemon.is_in_log(r"line 0 - logged by 'log-multi-lines'")
    assert l1.daemon.is_in_log(r"line 1 - logged by 'log-multi-lines'")
    assert l1.daemon.is_in_log(r"line 2 - logged by 'log-multi-lines'")


def test_errors(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/errors")
    l1 = node_factory.get_node(options={"plugin": plugin})
    with pytest.raises(RpcError, match=r"custom-error"):
        l1.rpc.call("custom-error")
    assert l1.daemon.is_in_log(r"Error while processing.*method.*custom-error")
    assert l1.daemon.is_in_log(r"code.*-100.*message.*custom-error")
    with pytest.raises(RpcError, match=r"Error while processing.*method.*execution-error"):
        l1.rpc.call("execution-error")
    assert l1.daemon.is_in_log(r"Error while processing.*method.*execution-error")
    assert l1.daemon.is_in_log(r":cause.*Divide by zero")
    with pytest.raises(RpcError, match=r"Error while processing.*:method.*non-json-writable-in-result"):
        l1.rpc.call("non-json-writable-in-result")
    assert l1.daemon.is_in_log(r"Error while processing.*method.*non-json-writable-in-result")
    assert l1.daemon.is_in_log(r":cause.*Don't know how to write JSON of class clojure.lang.Agent")
    assert l1.daemon.is_in_log(r":trace")
    with pytest.raises(RpcError, match=r"Error while processing.*:method.*non-json-writable-in-error"):
        l1.rpc.call("non-json-writable-in-error")
    assert l1.daemon.is_in_log(r"Error while processing.*method.*non-json-writable-in-error")


def test_async(node_factory, executor):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/async")
    l1 = node_factory.get_node(options={"plugin": plugin})
    start_time = time.time()
    # each call takes about 1 second
    f1 = executor.submit(l1.rpc.call, "async")
    f2 = executor.submit(l1.rpc.call, "async")
    f3 = executor.submit(l1.rpc.call, "async")
    counter = {f1.result()["counter"],
               f2.result()["counter"],
               f3.result()["counter"]}
    delta = time.time() - start_time
    assert counter == {1,2,3}
    # ensure the call to the method `async` are processed asynchronously
    assert delta < 1.5
