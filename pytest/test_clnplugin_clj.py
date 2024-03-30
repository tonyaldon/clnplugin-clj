from pyln.testing.fixtures import *
from pyln.client import RpcError
import os
import subprocess
import time
import re

os.chdir("pytest")

def test_rpcmethods(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/rpcmethods")
    l1 = node_factory.get_node(options={"plugin": plugin})
    assert l1.rpc.call("foo-0") == {"bar": "baz"}
    assert l1.rpc.call("foo-1", {"baz-1": "baz-1"}) == {"bar-1": "baz-1"}
    assert l1.rpc.call("foo-2") == {"bar-2": "baz-2"}
    # foo-3 must be call before foo-4 because it sets a value in plugin atom
    # that we want to get with foo-4
    assert l1.rpc.call("foo-3") == {}
    time.sleep(0.1)
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
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_options_mutli_dynamic")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_getinfo_internal_method")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_fn_not_defined")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_fn_not_a_function")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_fn_symbol_not_a_function")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_notifications_do_not_declare_log")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)
    plugin = os.path.join(os.getcwd(), "plugins/getmanifest_peer_connected_hook")
    with pytest.raises(RpcError, match=r"exited before replying to getmanifest"):
        l1.rpc.plugin_start(plugin)


def test_init(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/init")
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

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_check_opt")
    with pytest.raises(RpcError, match=r"Wrong option 'foo_1'"):
        l1.rpc.plugin_start(plugin, foo_1="foo-value")

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_check_opt_before_init_fn")
    with pytest.raises(RpcError, match=r"Wrong option 'foo_2'"):
        l1.rpc.plugin_start(plugin, foo_2="foo-value")

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_check_opt_not_a_function")
    with pytest.raises(RpcError, match=r":check-opt of.*foo_3.*option must be a function not.*[:a-vector \"is not a function\"].*which is an instance of.*class clojure.lang.PersistentVector"):
        l1.rpc.plugin_start(plugin, foo_3="foo-value")

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_check_opt_symbol_not_a_function")
    with pytest.raises(RpcError, match=r":check-opt of.*foo_.*option must be a function not .*some-symbol.*which is an instance of.*class clojure.lang.Symbol"):
        l1.rpc.plugin_start(plugin, foo_4="foo-value")

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_check_opt_execution_error")
    with pytest.raises(RpcError, match=r":check-opt of.*foo_5.* option thrown the following exception when called with.*foo-value.*value: #error.*:cause.*Divide by zero.*:type.*java.lang.ArithmeticException"):
        l1.rpc.plugin_start(plugin, foo_5="foo-value")

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_init_fn_not_a_function")
    with pytest.raises(RpcError, match=r":init-fn must be a function"):
        l1.rpc.plugin_start(plugin)

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_init_fn_execution_error")
    with pytest.raises(RpcError, match=r"#error.*:cause.*Divide by zero.*:via.*java.lang.ArithmeticException"):
        l1.rpc.plugin_start(plugin)

    plugin = os.path.join(os.getcwd(), "plugins/init_disable_init_fn")
    with pytest.raises(RpcError, match=r"disabled by user"):
        l1.rpc.plugin_start(plugin)


def test_plugin_process_killed_after_we_shutdown_lightningd(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/plugin_process_killed_after_we_shutdown_lightningd")
    l1 = node_factory.get_node(options={"plugin": plugin})
    line = l1.daemon.is_in_log(r'.*started\([0-9]*\).*plugins/plugin_process_killed_after_we_shutdown_lightningd')
    pidstr = re.search(r'.*started\(([0-9]*)\).*plugins/plugin_process_killed_after_we_shutdown_lightningd', line).group(1)

    # shutdown l1
    l1.stop()
    # I don't know how long do we need to wait
    # (1s seems enough, 5s should be good)
    time.sleep(5)
    # check that plugin's process has been killed
    proc = subprocess.run(["ps", "--pid", pidstr], stdout=subprocess.DEVNULL)
    # 1 means that ps can't select pidstr process, so it no longer exists
    # and plugin's process has been killed, and this is what we want.
    assert proc.returncode == 1


def test_options_deprecated(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/options_deprecated")

    l1 = node_factory.get_node(options={'allow-deprecated-apis': False})
    with pytest.raises(RpcError, match=r"deprecated option"):
        l1.rpc.plugin_start(plugin=plugin, foo_deprecated="foo-value")

    l2 = node_factory.get_node(options={'allow-deprecated-apis': True})
    l2.rpc.plugin_start(plugin=plugin, foo_deprecated="foo-value")
    listconfigs_resp = l2.rpc.listconfigs()
    assert listconfigs_resp["configs"]["foo_deprecated"]["value_str"] == "foo-value"
    assert l2.rpc.call("get-foo_deprecated-value") == {"foo_deprecated": "foo-value"}


def test_options_dynamic(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/options_dynamic")
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
    plugin = os.path.join(os.getcwd(), "plugins/log")
    l1 = node_factory.get_node(options={"plugin": plugin})
    l1.rpc.call("log-info")
    assert l1.daemon.is_in_log(r"INFO.*logged by 'log-info'")
    l1.rpc.call("log-debug")
    assert l1.daemon.is_in_log(r"DEBUG.*logged by 'log-debug'")
    l1.rpc.call("log-multi-lines")
    assert l1.daemon.is_in_log(r"line 0 - logged by 'log-multi-lines'")
    assert l1.daemon.is_in_log(r"line 1 - logged by 'log-multi-lines'")
    assert l1.daemon.is_in_log(r"line 2 - logged by 'log-multi-lines'")


def test_subscriptions_and_notifications(node_factory):
    plugins = [os.path.join(os.getcwd(), "plugins/subscriptions"),
               os.path.join(os.getcwd(), "plugins/notifications")]
    l1 = node_factory.get_node(options={"plugin": plugins})

    l1.rpc.call("notify-topic-0")
    l1.daemon.wait_for_log(r'Got a topic-0 notification {:foo-0 \\"bar-0\\"} from plugin notifications')
    l1.rpc.call("notify-topic-1")
    l1.daemon.wait_for_log(r"Got a topic-1 notification topic-1 params from plugin notifications")
    l1.rpc.call("notify-topic-undeclared-0")
    l1.daemon.wait_for_log(r'Plugin attempted to send a notification to topic "topic-undeclared-0" it hasn\'t declared in its manifest, not forwarding to subscribers.')
    time.sleep(1)
    assert not l1.daemon.is_in_log(r"Got a topic-undeclared-0 notification.*some params.* from plugin notifications")

    l1.rpc.call("notify-topic-1-non-json-writable")
    assert l1.daemon.is_in_log(r"Error while sending notification.*:method.*topic-1")
    assert l1.daemon.is_in_log(r":cause.*Don't know how to write JSON of class clojure.lang.Atom")

    # subscriptions.clj subscribed to 'connect' notification
    l2 = node_factory.get_node()
    l2.rpc.connect(l1.info['id'], 'localhost', l1.port)
    assert l1.daemon.wait_for_log("plugin-subscriptions: Got a connect notification")

    # as subscriptions.clj plugin subscribes to 'topic-undeclared-0'
    # and 'topic-undeclared-1' and notifications.clj doesn't declare these notification
    # topics, when l1 starts subscriptions_all.clj plugin which registers to all
    # notifications, it will receive the following warning notification
    #
    #     {"jsonrpc":"2.0","method":"warning","params":{"warning":{"level":"warn","time":"1711620196.233333777","timestamp":"2024-03-28T10:03:16.233Z","source":"plugin-subscriptions","log":"topic 'topic-undeclared-0' is not a known notification topic"}}}
    #     {"jsonrpc":"2.0","method":"warning","params":{"warning":{"level":"warn","time":"1711620196.233345766","timestamp":"2024-03-28T10:03:16.233Z","source":"plugin-subscriptions","log":"topic 'topic-undeclared-1' is not a known notification topic"}}}
    #
    # before receiving the init request.
    # Here we test that we take this fact into account!
    plugin_subscriptions_all = os.path.join(os.getcwd(), "plugins/subscriptions_all")
    l1.rpc.plugin_start(plugin_subscriptions_all)
    l1.daemon.wait_for_log(r"plugin-subscriptions_all: Got a warning notification before the init request:.*topic-undeclared-0")
    l1.daemon.wait_for_log(r"plugin-subscriptions_all: Got a warning notification before the init request:.*topic-undeclared-1")

    # connect topic: subscriptions_all.clj subscribed to '*' notifications (example of connect topic)
    l2 = node_factory.get_node()
    l2.rpc.connect(l1.info['id'], 'localhost', l1.port)
    l1.daemon.wait_for_log("plugin-subscriptions_all: Got a connect notification")

    # shutdown topic: subscriptions_all.clj subscribed to '*' notifications (example of s)
    l1.rpc.plugin_stop(plugin_subscriptions_all)
    l1.daemon.wait_for_log("plugin-subscriptions_all: subscriptions.clj plugin shutting down itself")
    l1.daemon.wait_for_log("plugin-subscriptions_all: Killing plugin: exited during normal operation")


def test_notifications_message_progress(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/notifications_message_progress")
    l1 = node_factory.get_node(options={"plugin": plugin})
    l1_info = l1.rpc.getinfo()
    l1_socket_file = os.path.join(l1_info["lightning-dir"], "lightning-rpc")

    # Call send-message-notifications defined in notifications_message_progress.clj plugin.
    # Accumulate in an vector the 3 notifications queued in a channel
    # and the response at the end
    cmd = f"clojure -X rpc/call-send-message-notifications :socket-file '\"{l1_socket_file}\"'"
    cmd_str = os.popen(cmd).read()
    assert json.loads(cmd_str) == ["foo","bar","baz",{"foo":"bar"}]


    # Call send-progress-notifications defined in notifications_message_progress.clj plugin.
    # Accumulate in an vector the 3 notifications queued in a channel
    # and the response at the end
    cmd = f"clojure -X rpc/call-send-progress-notifications :socket-file '\"{l1_socket_file}\"'"
    cmd_str = os.popen(cmd).read()
    # assert json.loads(cmd_str) == [0,1,2,{"foo":"bar"}]
    assert json.loads(cmd_str) == [{"num": 0, "total": 3},
                                   {"num": 1, "total": 3},
                                   {"num": 2, "total": 3},
                                   {"foo":"bar"}]

    # Call send-progress-notifications-with-stages defined in notifications_message_progress.clj plugin.
    # Accumulate in an vector the 6 notifications queued in a channel
    # and the response at the end
    cmd = f"clojure -X rpc/call-send-progress-notifications :with-stages? 'true' :socket-file '\"{l1_socket_file}\"'"
    cmd_str = os.popen(cmd).read()
    assert json.loads(cmd_str) == [{"num": 0, "total": 2, "stage": {"num": 0, "total": 3}},
                                   {"num": 1, "total": 2, "stage": {"num": 0, "total": 3}},
                                   {"num": 0, "total": 2, "stage": {"num": 1, "total": 3}},
                                   {"num": 1, "total": 2, "stage": {"num": 1, "total": 3}},
                                   {"num": 0, "total": 2, "stage": {"num": 2, "total": 3}},
                                   {"num": 1, "total": 2, "stage": {"num": 2, "total": 3}},
                                   {"foo":"bar"}]

    with pytest.raises(RpcError, match=r"Error while processing.*method.*wrong-args-in-notify-progress"):
        l1.rpc.call("wrong-args-in-notify-progress")
    assert l1.daemon.is_in_log(r"Error while processing.*method.*wrong-args-in-notify-progress")
    assert l1.daemon.is_in_log(r":cause.*Assert failed:")


def test_errors(node_factory):
    plugin = os.path.join(os.getcwd(), "plugins/errors")
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
    # In the async... plugins we are Thread/sleep which block
    # the thread of execution, so the machine running this test
    # must be able to run 4 threads in parallel to get this test
    # to pass

    # max-parallel-reqs not specified in plugin so default to 512
    plugin = os.path.join(os.getcwd(), "plugins/async")
    l1 = node_factory.get_node(options={"plugin": plugin})
    start_time = time.time()
    # each call takes about 1 second
    f1 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    f2 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    f3 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    counter = {f1.result()["counter"],
               f2.result()["counter"],
               f3.result()["counter"]}
    delta = time.time() - start_time
    assert counter == {1,2,3}
    # ensure the call to the method `sleep-and-update-counter` are processed asynchronously
    assert delta < 1.5

    # max-parallel-reqs set to 1 which makes plugin to process
    # lightningd requests synchronously
    plugin = os.path.join(os.getcwd(), "plugins/async_sync")
    l1 = node_factory.get_node(options={"plugin": plugin})
    start_time = time.time()
    # each call takes about 1 second
    f1 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    f2 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    f3 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    counter = {f1.result()["counter"],
               f2.result()["counter"],
               f3.result()["counter"]}
    delta = time.time() - start_time
    assert counter == {1,2,3}
    # ensure the call to the method `sleep-and-update-counter` are processed synchronously
    assert delta > 3.0

    # max-parallel-reqs set to 2
    plugin = os.path.join(os.getcwd(), "plugins/async_max_parallel_reqs")
    l1 = node_factory.get_node(options={"plugin": plugin})
    start_time = time.time()
    # each call takes about 1 second
    f1 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    f2 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    f3 = executor.submit(l1.rpc.call, "sleep-and-update-counter")
    counter = {f1.result()["counter"],
               f2.result()["counter"],
               f3.result()["counter"]}
    delta = time.time() - start_time
    assert counter == {1,2,3}
    # ensure at most 2 `sleep-and-update-counter` request processed at the same time
    assert delta > 2
    # ensure at 2 `sleep-and-update-counter` request processed at the same time
    assert delta < 3


def test_hooks_peer_connected(node_factory):
    plugins = [os.path.join(os.getcwd(), "plugins/hooks_peer_connected_foo"),
               os.path.join(os.getcwd(), "plugins/hooks_peer_connected_bar"),
               os.path.join(os.getcwd(), "plugins/hooks_peer_connected_baz")]
    l1 = node_factory.get_node(options={"plugin": plugins})
    l2 = node_factory.get_node()
    l2.rpc.connect(l1.info['id'], 'localhost', l1.port)

    l1.daemon.wait_for_log("plugin-hooks_peer_connected_foo: timestamp:")
    line_foo = l1.daemon.is_in_log('plugin-hooks_peer_connected_foo: timestamp:')
    timestamp_foo = int(re.search(r'timestamp: ([0-9]*)', line_foo).group(1))
    line_bar = l1.daemon.is_in_log('plugin-hooks_peer_connected_bar: timestamp:')
    timestamp_bar = int(re.search(r'timestamp: ([0-9]*)', line_bar).group(1))
    line_baz = l1.daemon.is_in_log('plugin-hooks_peer_connected_baz: timestamp:')
    timestamp_baz = int(re.search(r'timestamp: ([0-9]*)', line_baz).group(1))

    # peer_connected is a chained hook and plugins hooks_peer_connected_foo,
    # hooks_peer_connected_bar and hooks_peer_connected_baz register to it and
    # are called in chained, foo first then bar then baz.  They all log a timestamp
    # and return {"result": "continue"}.
    # So we must have the following to be true
    assert timestamp_foo < timestamp_bar < timestamp_baz


def test_java(node_factory):
    os.popen("cd plugins/java && clojure -T:build plugin").read()
    plugin = os.path.join(os.getcwd(), "plugins/java/target/myplugin")
    l1 = node_factory.get_node(options={"plugin": plugin})
    assert l1.rpc.call("foo") == {"bar": "baz"}
