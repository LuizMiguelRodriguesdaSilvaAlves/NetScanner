import socket

import pytest

from netscanner import port_scanner
from netscanner.port_scanner import (
    InvalidPortScanOptionsError,
    InvalidPortScanTargetError,
    InvalidPortSpecError,
    PortScanTargetOutOfScopeError,
    TooManyPortsError,
    scan_ports,
)
from netscanner import protocol


def test_scan_detects_a_local_open_tcp_port(monkeypatch):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.bind(("127.0.0.1", 0))
        server.listen(1)
        port = server.getsockname()[1]
        result = scan_ports({
            "target": "127.0.0.1",
            "ports": str(port),
            "timeout_ms": 200,
            "max_workers": 1,
        })

    assert result["target"] == "127.0.0.1"
    assert result["total_ports"] == 1
    assert result["scanned_ports"] == 1
    assert result["open_count"] == 1
    assert result["open_ports"] == [port]
    assert result["results"][0]["state"] == "open"
    assert result["method"] == "tcp_connect"
    assert result["scanned_at"]


def test_scan_returns_sorted_results_and_removes_duplicate_ports(monkeypatch):
    def fake_scan(target, port, *, timeout_seconds):
        return {"port": port, "state": "closed", "latency_ms": 1.0}

    monkeypatch.setattr(port_scanner, "_scan_port", fake_scan)
    result = scan_ports({"target": "127.0.0.1", "ports": "81,80,80,82", "max_workers": 2})

    assert result["total_ports"] == 3
    assert [item["port"] for item in result["results"]] == [80, 81, 82]
    assert result["closed_count"] == 3
    assert result["filtered_count"] == 0


@pytest.mark.parametrize("target", ["", "127.0.0.1/32", "not-an-ip", "2001:db8::1"])
def test_invalid_target_is_rejected(target):
    with pytest.raises(InvalidPortScanTargetError):
        scan_ports({"target": target, "ports": "80"})


@pytest.mark.parametrize("target", ["8.8.8.8", "169.254.1.1", "224.0.0.1", "0.0.0.0"])
def test_out_of_scope_target_is_rejected(target):
    with pytest.raises(PortScanTargetOutOfScopeError):
        scan_ports({"target": target, "ports": "80"})


@pytest.mark.parametrize("port_spec", ["", "0", "65536", "80-", "-80", "90-80", "80,,443", "abc", "1-65535"])
def test_invalid_or_oversized_port_spec_is_rejected(port_spec):
    with pytest.raises((InvalidPortSpecError, TooManyPortsError)):
        scan_ports({"target": "127.0.0.1", "ports": port_spec})


def test_port_ranges_are_expanded_and_sorted(monkeypatch):
    monkeypatch.setattr(
        port_scanner,
        "_scan_port",
        lambda target, port, *, timeout_seconds: {
            "port": port, "state": "filtered", "latency_ms": 0.1
        },
    )
    result = scan_ports({"target": "127.0.0.1", "ports": "443,80-82", "max_workers": 4})

    assert [item["port"] for item in result["results"]] == [80, 81, 82, 443]
    assert result["filtered_count"] == 4


@pytest.mark.parametrize("payload", [
    {"target": "127.0.0.1", "ports": "80", "timeout_ms": 99},
    {"target": "127.0.0.1", "ports": "80", "timeout_ms": 3001},
    {"target": "127.0.0.1", "ports": "80", "max_workers": 0},
    {"target": "127.0.0.1", "ports": "80", "max_workers": 129},
    {"target": "127.0.0.1", "ports": "80", "unexpected": True},
])
def test_invalid_options_are_rejected(payload):
    with pytest.raises(InvalidPortScanOptionsError):
        scan_ports(payload)


def test_boolean_timeout_is_rejected():
    with pytest.raises(InvalidPortScanOptionsError):
        scan_ports({"target": "127.0.0.1", "ports": "80", "timeout_ms": True})


def test_protocol_routes_port_scan_and_preserves_request_id(monkeypatch):
    monkeypatch.setattr(
        protocol,
        "scan_ports",
        lambda payload: {"target": "127.0.0.1", "total_ports": 1, "open_ports": [80]},
    )
    response = protocol.handle_request({
        "protocol_version": protocol.PROTOCOL_VERSION,
        "request_id": "port-scan-test-001",
        "action": "network.port_scan",
        "payload": {"target": "127.0.0.1", "ports": "80"},
    })

    assert response["success"] is True
    assert response["request_id"] == "port-scan-test-001"
    assert response["data"]["open_ports"] == [80]
