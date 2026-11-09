import socket

import pytest

from netscanner import host_discovery
from netscanner.host_discovery import (
    InvalidDiscoveryOptionsError,
    InvalidTargetError,
    NetworkTooLargeError,
    TargetOutOfScopeError,
    discover_hosts,
)
from netscanner.protocol import PROTOCOL_VERSION, handle_request


def test_discovery_returns_sorted_hosts_and_summary(monkeypatch):
    def fake_probe(address, *, timeout_seconds, ports=host_discovery.DEFAULT_PORTS):
        if str(address) == "127.0.0.2":
            return None
        return {
            "ip": str(address),
            "hostname": None,
            "open_ports": [80],
            "latency_ms": 1.2,
            "discovery_method": "tcp_connect",
        }

    monkeypatch.setattr(host_discovery, "_probe_host", fake_probe)
    result = discover_hosts({"cidr": "127.0.0.0/30", "timeout_ms": 200, "max_workers": 2})

    assert result["target"] == "127.0.0.0/30"
    assert result["scanned_hosts"] == 2
    assert result["active_hosts"] == 1
    assert result["hosts"][0]["ip"] == "127.0.0.1"
    assert result["hosts"][0]["open_ports"] == [80]
    assert result["method"] == "tcp_connect"
    assert result["scanned_at"]


def test_tcp_probe_detects_a_local_open_port(monkeypatch):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.bind(("127.0.0.1", 0))
        server.listen(1)
        port = server.getsockname()[1]
        monkeypatch.setattr(host_discovery, "DEFAULT_PORTS", (port,))
        monkeypatch.setattr(host_discovery, "_resolve_hostname", lambda _: None)
        result = discover_hosts({"cidr": "127.0.0.1/32", "timeout_ms": 200, "max_workers": 1})

    assert result["active_hosts"] == 1
    assert result["hosts"][0]["open_ports"] == [port]


@pytest.mark.parametrize("target", ["", "192.168.1.1", "not-a-network", "2001:db8::/64"])
def test_invalid_target_is_rejected(target):
    with pytest.raises(InvalidTargetError):
        discover_hosts({"cidr": target})


def test_public_target_is_rejected():
    with pytest.raises(TargetOutOfScopeError):
        discover_hosts({"cidr": "8.8.8.0/24"})


def test_network_larger_than_limit_is_rejected():
    with pytest.raises(NetworkTooLargeError):
        discover_hosts({"cidr": "192.168.0.0/21"})


@pytest.mark.parametrize("payload", [
    {"cidr": "127.0.0.1/32", "timeout_ms": 99},
    {"cidr": "127.0.0.1/32", "timeout_ms": 3001},
    {"cidr": "127.0.0.1/32", "max_workers": 0},
    {"cidr": "127.0.0.1/32", "max_workers": 129},
    {"cidr": "127.0.0.1/32", "unexpected": True},
])
def test_invalid_options_are_rejected(payload):
    with pytest.raises(InvalidDiscoveryOptionsError):
        discover_hosts(payload)


def test_boolean_timeout_is_rejected():
    with pytest.raises(InvalidDiscoveryOptionsError):
        discover_hosts({"cidr": "127.0.0.1/32", "timeout_ms": True})


def test_protocol_routes_host_discovery_and_preserves_request_id():
    request = {
        "protocol_version": PROTOCOL_VERSION,
        "request_id": "discovery-test-001",
        "action": "network.host_discovery",
        "payload": {"cidr": "8.8.8.0/24"},
    }

    response = handle_request(request)

    assert response["success"] is False
    assert response["request_id"] == "discovery-test-001"
    assert response["error"]["code"] == "TARGET_OUT_OF_SCOPE"
