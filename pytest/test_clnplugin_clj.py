from pyln.testing.fixtures import *
import os

def test_foo(node_factory):
    plugin = os.path.join(os.getcwd(), "pytest/plugins/foo")
    l1 = node_factory.get_node(options={"plugin": plugin})
    l1_info = l1.rpc.getinfo()
    assert l1.rpc.call("foo") == {"bar": "baz"}
