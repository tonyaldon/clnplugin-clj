from pyln.testing.fixtures import *
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
