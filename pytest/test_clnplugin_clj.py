from pyln.testing.fixtures import *
from pyln.client import RpcError
import os
import time

def test_foo(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/foo")
    l1 = node_factory.get_node(options={"plugin": plugin})
    assert l1.rpc.call("foo") == {"bar": "baz"}


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

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_not_a_function")
    with pytest.raises(RpcError, match=r":init-fn must be a function"):
        l1.rpc.plugin_start(plugin)

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable_execution_error")
    with pytest.raises(RpcError, match=r"java.lang.ArithmeticException"):
        l1.rpc.plugin_start(plugin)

    plugin = os.path.join(os.getcwd(), "pytest/plugins/init_disable")
    with pytest.raises(RpcError, match=r"disabled by user"):
        l1.rpc.plugin_start(plugin)


def test_deprecated_options(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/deprecated_options")

    l1 = node_factory.get_node(options={'allow-deprecated-apis': False})
    with pytest.raises(RpcError, match=r"deprecated option"):
        l1.rpc.plugin_start(plugin=plugin, foo_deprecated="foo-value")

    l2 = node_factory.get_node(options={'allow-deprecated-apis': True})
    l2.rpc.plugin_start(plugin=plugin, foo_deprecated="foo-value")
    listconfigs_resp = l2.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo_deprecated"]["value_str"] == "foo-value"
    assert l2.rpc.call("get-foo_deprecated-value") == {"foo_deprecated": "foo-value"}


def test_dynamic_options(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/dynamic_options")
    l1 = node_factory.get_node(options={"plugin": plugin})
    setconfig_resp = l1.rpc.setconfig(config="foo-dynamic", val="foo-value-0")
    assert setconfig_resp["config"]["value_str"] == "foo-value-0"
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-dynamic"]["value_str"] == "foo-value-0"
    assert l1.rpc.call("get-foo-dynamic-value") == {"foo-dynamic": "foo-value-0"}

    l1.stop()
    l1.start()
    assert listconfigs_resp["configs"]["foo-dynamic"]["value_str"] == "foo-value-0"
    assert l1.rpc.call("get-foo-dynamic-value") == {"foo-dynamic": "foo-value-0"}

    setconfig_resp = l1.rpc.setconfig(config="foo-dynamic", val="foo-value-1")
    assert setconfig_resp["config"]["value_str"] == "foo-value-1"
    listconfigs_resp = l1.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo-dynamic"]["value_str"] == "foo-value-1"
    assert l1.rpc.call("get-foo-dynamic-value") == {"foo-dynamic": "foo-value-1"}


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
    assert l1.daemon.is_in_log(r"java.lang.ArithmeticException: Divide by zero")


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
