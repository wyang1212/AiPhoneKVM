# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
"""Mosquitto's dynamic-security API; management credentials never leave the server."""
import json
import threading
import time
import uuid
from collections import OrderedDict

import paho.mqtt.client as mqtt


class BrokerError(Exception):
    pass


def device_roles(device):
    prefix, ident = device["topic_prefix"], device["device_id"]
    topics = {kind: f"{prefix}{kind}/{ident}" for kind in ("command", "response", "status", "log")}
    roles = []
    for role, reads, writes in (
        ("phone", ["command"], ["response", "status", "log"]),
        ("controller", ["response", "status", "log"], ["command"]),
    ):
        acls = [{"acltype": "publishClientSend", "topic": topics[k], "allow": True} for k in writes]
        for kind in reads:
            acls.extend({"acltype": acl, "topic": topics[kind], "allow": True}
                        for acl in ("subscribeLiteral", "publishClientReceive"))
        roles.append({"command": "createRole", "rolename": device[role + "_user"], "acls": acls})
    return roles


class Broker:
    def __init__(self, config):
        self.config = config
        self.ready = threading.Event()
        self.lock = threading.Lock()
        self.rpc_lock = threading.Lock()
        self.pending = {}
        self.statuses = OrderedDict()
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                                  client_id="portal-service-" + uuid.uuid4().hex)
        self.client.username_pw_set(config["username"], config["password"])
        self.client.on_connect = self._connect
        self.client.on_subscribe = self._subscribed
        self.client.on_disconnect = lambda *args: self.ready.clear()
        self.client.on_message = self._message

    def start(self):
        self.client.connect_async(self.config.get("host", "127.0.0.1"), self.config["port"], 20)
        self.client.loop_start()

    def close(self):
        self.client.disconnect()
        self.client.loop_stop()

    def _connect(self, client, userdata, flags, reason, properties):
        if not reason.is_failure:
            client.subscribe([("$CONTROL/dynamic-security/v1/response", 1),
                              ("bluehidflow/#", 1), ("aiphonekvm/#", 1)])

    def _subscribed(self, client, userdata, mid, reasons, properties):
        if all(not reason.is_failure for reason in reasons):
            self.ready.set()

    def _message(self, client, userdata, message):
        try:
            if len(message.payload) > 65536:
                return
            data = json.loads(message.payload)
            if not isinstance(data, dict):
                return
        except (ValueError, UnicodeDecodeError):
            return
        with self.lock:
            if message.topic == "$CONTROL/dynamic-security/v1/response":
                for response in data.get("responses", []):
                    waiter = self.pending.get(response.get("correlationData"))
                    if waiter:
                        waiter["responses"].append(response)
                        if len(waiter["responses"]) >= waiter["count"]:
                            waiter["event"].set()
            else:
                waiter = self.pending.get((message.topic, data.get("id")))
                if waiter:
                    waiter["responses"].append(data)
                    waiter["event"].set()
                if "/status/" in message.topic:
                    self.statuses[message.topic] = (data, time.time())
                    self.statuses.move_to_end(message.topic)
                    while len(self.statuses) > 6000:
                        self.statuses.popitem(last=False)

    def _request(self, key, topic, payload, count, timeout):
        if not self.ready.wait(5):
            raise BrokerError("消息服务暂不可用，请稍后重试")
        waiter = {"event": threading.Event(), "responses": [], "count": count}
        with self.lock:
            if len(self.pending) >= 64:
                raise BrokerError("当前请求较多，请稍后重试")
            self.pending[key] = waiter
        try:
            published = self.client.publish(topic, json.dumps(payload), qos=1, retain=False)
            if published.rc != mqtt.MQTT_ERR_SUCCESS or not waiter["event"].wait(timeout):
                raise BrokerError("未收到回复，请检查主控端连接；操作指令请勿盲目重发")
            return waiter["responses"]
        finally:
            with self.lock:
                self.pending.pop(key, None)

    def rpc(self, commands, missing_ok=False):
        with self.rpc_lock:
            correlation = uuid.uuid4().hex
            enriched = [dict(command, correlationData=correlation) for command in commands]
            responses = self._request(correlation, "$CONTROL/dynamic-security/v1",
                                      {"commands": enriched}, len(commands), 10)
            if any("error" in r and not (missing_ok and "not found" in str(r["error"]).lower()) for r in responses):
                raise BrokerError("设备连接配置未完成，请稍后重试")
            return responses

    def provision(self, device, passwords):
        commands = device_roles(device)
        for role in ("phone", "controller"):
            commands.append({"command": "createClient", "username": device[role + "_user"],
                             "password": passwords[role], "clientid": device[role + "_client_id"],
                             "roles": [{"rolename": device[role + "_user"]}]})
        try:
            self.rpc(commands)
        except BrokerError:
            self.remove(device, ignore_errors=True)
            raise

    def enabled(self, device, enabled):
        self.rpc([{"command": "enableClient" if enabled else "disableClient",
                   "username": device[role + "_user"]} for role in ("phone", "controller")])

    def rotate(self, device, passwords):
        self.enabled(device, False)  # Disconnect existing sessions before changing passwords.
        self.rpc([{"command": "setClientPassword", "username": device[role + "_user"],
                   "password": passwords[role]} for role in ("phone", "controller")])
        if device["enabled"]:
            self.enabled(device, True)

    def remove(self, device, ignore_errors=False, missing_ok=False):
        commands = [{"command": "deleteClient", "username": device[role + "_user"]}
                    for role in ("phone", "controller")]
        commands += [{"command": "deleteRole", "rolename": device[role + "_user"]}
                     for role in ("phone", "controller")]
        if ignore_errors:
            for command in commands:
                try:
                    self.rpc([command])
                except BrokerError:
                    pass
        else:
            self.rpc(commands, missing_ok=missing_ok)

    def command(self, device, action, params=None, request_id=None):
        ident = request_id or str(uuid.uuid4())
        prefix = device["topic_prefix"]
        target = device["device_id"]
        return self._request((f"{prefix}response/{target}", ident), f"{prefix}command/{target}",
                             {"version": "1.0", "id": ident, "action": action, "params": params or {}}, 1, 12)[0]

    @staticmethod
    def task_status(data):
        task = data.get("aiTask")
        if not isinstance(task, dict):
            task = {}
        # Only the owning account receives this bounded task view; never return model configuration.
        result = {k: str(task.get(k, ""))[:n] for k, n in
                  (("taskId", 64), ("status", 32), ("label", 40), ("goal", 2000),
                   ("summary", 2000), ("message", 2000))}
        result["active"] = task.get("active") is True
        for key in ("steps", "rounds", "maxSteps", "maxMinutes", "startedAt", "endedAt"):
            value = task.get(key, 0)
            result[key] = value if type(value) is int and value >= 0 else 0
        return result

    def status(self, device):
        topic = f"{device['topic_prefix']}status/{device['device_id']}"
        with self.lock:
            data, received = self.statuses.get(topic, ({}, 0))
        timestamp = data.get("timestamp", 0)
        fresh = isinstance(timestamp, (int, float)) and abs(time.time() - timestamp / 1000) < 90
        online = bool(device["enabled"] and time.time() - received < 75 and fresh
                      and data.get("running") and data.get("status") != "offline")
        return {"online": online, "bluetooth_connected": online and data.get("bluetoothConnected") is True,
                "target": str(data.get("target", ""))[:100] if online else "",
                "last_seen": received or None,
                "remote_ai": online and data.get("remoteAi") == 1,
                "model_configured": online and data.get("modelConfigured") is True,
                "ai_task": self.task_status(data) if online else None}

    def pairing_proven(self, device, challenge, since):
        # Only the device's phone role may publish this exact status topic.
        topic = f"{device['topic_prefix']}status/{device['device_id']}"
        with self.lock:
            data, received = self.statuses.get(topic, ({}, 0))
        return received >= since and data.get("pairing_proof") == challenge
