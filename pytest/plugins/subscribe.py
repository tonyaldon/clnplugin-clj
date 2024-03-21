#!/usr/bin/env python3
from pyln.client import Plugin


plugin = Plugin()


@plugin.subscribe("topic-0")
def on_custom_notification(origin, payload, **kwargs):
    plugin.log(f"Got a topic-0 notification {payload} from plugin {origin}")


@plugin.subscribe("topic-1")
def on_custom_notification(origin, payload, **kwargs):
    plugin.log(f"Got a topic-1 notification {payload} from plugin {origin}")

@plugin.subscribe("topic-undeclared")
def on_custom_notification(origin, payload, **kwargs):
    plugin.log(f"Got a topic-undeclared notification {payload} from plugin {origin}")


plugin.run()
