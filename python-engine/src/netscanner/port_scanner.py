"""Bounded TCP connect scanning for explicitly authorized private IPv4 hosts."""

from __future__ import annotations

import errno
import ipaddress
import socket
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from typing import Any

MAX_PORTS = 1024
MAX_WORKERS = 128
MIN_TIMEOUT_MS = 100
MAX_TIMEOUT_MS = 3000
DEFAULT_TIMEOUT_MS = 500
DEFAULT_WORKERS = 64

_ALLOWED_NETWORKS = tuple(
    ipaddress.ip_network(value)
    for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8")
)


class PortScanError(ValueError):
    """Base class for safe, user-correctable port scan errors."""

    code = "PORT_SCAN_ERROR"


class InvalidPortScanTargetError(PortScanError):
    code = "INVALID_TARGET"


class PortScanTargetOutOfScopeError(PortScanError):
    code = "TARGET_OUT_OF_SCOPE"


class InvalidPortSpecError(PortScanError):
    code = "INVALID_PORT_SPEC"


class TooManyPortsError(PortScanError):
    code = "TOO_MANY_PORTS"


class InvalidPortScanOptionsError(PortScanError):
    code = "INVALID_PORT_SCAN_OPTIONS"


def _validate_target(value: Any) -> ipaddress.IPv4Address:
    if not isinstance(value, str) or not value.strip() or "/" in value:
        raise InvalidPortScanTargetError(
            "target must be one IPv4 address (not a CIDR range), for example 192.168.1.10."
        )
    try:
        address = ipaddress.ip_address(value.strip())
    except ValueError as exc:
        raise InvalidPortScanTargetError("target is not a valid IP address.") from exc

    if not isinstance(address, ipaddress.IPv4Address):
        raise InvalidPortScanTargetError("Only IPv4 targets are supported in this version.")
    if address.is_multicast or address.is_unspecified or address.is_link_local or address.is_reserved:
        raise PortScanTargetOutOfScopeError("The selected IPv4 address is not an allowed host target.")
    if not any(address in network for network in _ALLOWED_NETWORKS):
        raise PortScanTargetOutOfScopeError(
            "Only RFC1918 private IPv4 addresses and loopback are allowed."
        )
    return address


def _parse_ports(value: Any) -> tuple[int, ...]:
    if not isinstance(value, str) or not value.strip():
        raise InvalidPortSpecError(
            "ports must be a comma-separated list or range, for example 22,80,443,8000-8100."
        )

    selected: set[int] = set()
    for raw_token in value.split(","):
        token = raw_token.strip()
        if not token:
            raise InvalidPortSpecError("The port list contains an empty item.")

        if "-" in token:
            if token.count("-") != 1:
                raise InvalidPortSpecError(f"Invalid port range: {token!r}.")
            start_text, end_text = (part.strip() for part in token.split("-", 1))
            if not start_text.isascii() or not start_text.isdigit() or not end_text.isascii() or not end_text.isdigit():
                raise InvalidPortSpecError(f"Invalid port range: {token!r}.")
            start, end = int(start_text), int(end_text)
            if not 1 <= start <= 65535 or not 1 <= end <= 65535 or end < start:
                raise InvalidPortSpecError(f"Port range must satisfy 1 <= start <= end <= 65535: {token!r}.")
            if end - start + 1 > MAX_PORTS:
                raise TooManyPortsError(f"A single port range cannot exceed {MAX_PORTS} ports.")
            selected.update(range(start, end + 1))
        else:
            if not token.isascii() or not token.isdigit():
                raise InvalidPortSpecError(f"Invalid port number: {token!r}.")
            port = int(token)
            if not 1 <= port <= 65535:
                raise InvalidPortSpecError("Ports must be between 1 and 65535.")
            selected.add(port)

        if len(selected) > MAX_PORTS:
            raise TooManyPortsError(f"A scan may include at most {MAX_PORTS} unique ports.")

    if not selected:
        raise InvalidPortSpecError("At least one TCP port must be selected.")
    return tuple(sorted(selected))


def _validate_options(payload: dict[str, Any]) -> tuple[int, int]:
    allowed_keys = {"target", "ports", "timeout_ms", "max_workers"}
    unknown_keys = set(payload) - allowed_keys
    if unknown_keys:
        raise InvalidPortScanOptionsError(
            "Unsupported port scan option(s): " + ", ".join(sorted(unknown_keys)) + "."
        )

    timeout_ms = payload.get("timeout_ms", DEFAULT_TIMEOUT_MS)
    max_workers = payload.get("max_workers", DEFAULT_WORKERS)

    if isinstance(timeout_ms, bool) or not isinstance(timeout_ms, int):
        raise InvalidPortScanOptionsError("timeout_ms must be an integer.")
    if not MIN_TIMEOUT_MS <= timeout_ms <= MAX_TIMEOUT_MS:
        raise InvalidPortScanOptionsError(
            f"timeout_ms must be between {MIN_TIMEOUT_MS} and {MAX_TIMEOUT_MS}."
        )
    if isinstance(max_workers, bool) or not isinstance(max_workers, int):
        raise InvalidPortScanOptionsError("max_workers must be an integer.")
    if not 1 <= max_workers <= MAX_WORKERS:
        raise InvalidPortScanOptionsError(f"max_workers must be between 1 and {MAX_WORKERS}.")
    return timeout_ms, max_workers


def _scan_port(target: str, port: int, *, timeout_seconds: float) -> dict[str, Any]:
    """Classify one TCP connect attempt without sending application data."""
    started = time.perf_counter()
    state = "filtered"
    refused_errors = {errno.ECONNREFUSED, 10061}

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as connection:
            connection.settimeout(timeout_seconds)
            result = connection.connect_ex((target, port))
        if result == 0:
            state = "open"
        elif result in refused_errors:
            state = "closed"
    except (OSError, TimeoutError):
        # A timeout or other connection error is inconclusive, not proof the port is closed.
        state = "filtered"

    return {
        "port": port,
        "state": state,
        "latency_ms": round((time.perf_counter() - started) * 1000, 2),
    }


def scan_ports(payload: dict[str, Any]) -> dict[str, Any]:
    """Scan a bounded set of TCP ports on one validated private IPv4 address."""
    if not isinstance(payload, dict):
        raise InvalidPortScanOptionsError("The port scan payload must be a JSON object.")

    target = _validate_target(payload.get("target"))
    ports = _parse_ports(payload.get("ports"))
    timeout_ms, max_workers = _validate_options(payload)
    worker_count = min(max_workers, len(ports))
    started = time.perf_counter()

    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=worker_count, thread_name_prefix="netscanner-port-scan") as executor:
        futures = {
            executor.submit(_scan_port, str(target), port, timeout_seconds=timeout_ms / 1000): port
            for port in ports
        }
        for future in as_completed(futures):
            results.append(future.result())

    results.sort(key=lambda item: item["port"])
    open_ports = [item["port"] for item in results if item["state"] == "open"]
    closed_count = sum(item["state"] == "closed" for item in results)
    filtered_count = sum(item["state"] == "filtered" for item in results)

    return {
        "target": str(target),
        "total_ports": len(ports),
        "scanned_ports": len(results),
        "open_count": len(open_ports),
        "closed_count": closed_count,
        "filtered_count": filtered_count,
        "open_ports": open_ports,
        "duration_ms": round((time.perf_counter() - started) * 1000, 2),
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "method": "tcp_connect",
        "timeout_ms": timeout_ms,
        "max_workers": worker_count,
        "results": results,
    }
