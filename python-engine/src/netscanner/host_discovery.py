"""Bounded TCP-based discovery for explicitly authorized local IPv4 networks."""

from __future__ import annotations

import errno
import ipaddress
import socket
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from typing import Any

DEFAULT_PORTS = (22, 53, 80, 443, 445, 3389)
MAX_TARGET_HOSTS = 1024
MAX_WORKERS = 128
MIN_TIMEOUT_MS = 100
MAX_TIMEOUT_MS = 3000
DEFAULT_TIMEOUT_MS = 500
DEFAULT_WORKERS = 64

_ALLOWED_NETWORKS = tuple(
    ipaddress.ip_network(value)
    for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8")
)


class DiscoveryError(ValueError):
    """Base class for safe, user-correctable discovery errors."""

    code = "DISCOVERY_ERROR"


class InvalidTargetError(DiscoveryError):
    code = "INVALID_TARGET"


class TargetOutOfScopeError(DiscoveryError):
    code = "TARGET_OUT_OF_SCOPE"


class NetworkTooLargeError(DiscoveryError):
    code = "NETWORK_TOO_LARGE"


class InvalidDiscoveryOptionsError(DiscoveryError):
    code = "INVALID_DISCOVERY_OPTIONS"


def _host_count(network: ipaddress.IPv4Network) -> int:
    if network.prefixlen >= 31:
        return network.num_addresses
    return max(0, network.num_addresses - 2)


def _validate_target(cidr: Any) -> ipaddress.IPv4Network:
    if not isinstance(cidr, str) or not cidr.strip() or "/" not in cidr:
        raise InvalidTargetError("The target must be an IPv4 CIDR, for example 192.168.1.0/24.")
    try:
        network = ipaddress.ip_network(cidr.strip(), strict=False)
    except ValueError as exc:
        raise InvalidTargetError("The target is not a valid IPv4 CIDR network.") from exc
    if not isinstance(network, ipaddress.IPv4Network):
        raise InvalidTargetError("Only IPv4 networks are supported in this version.")
    if not any(network.subnet_of(allowed) for allowed in _ALLOWED_NETWORKS):
        raise TargetOutOfScopeError(
            "Only RFC1918 private IPv4 ranges and loopback are allowed by this discovery version."
        )
    count = _host_count(network)
    if count < 1:
        raise InvalidTargetError("The target network does not contain any usable host addresses.")
    if count > MAX_TARGET_HOSTS:
        raise NetworkTooLargeError(
            f"The target contains {count} hosts; the current limit is {MAX_TARGET_HOSTS}."
        )
    return network


def _validate_options(payload: dict[str, Any]) -> tuple[int, int]:
    allowed_keys = {"cidr", "timeout_ms", "max_workers"}
    unknown_keys = set(payload) - allowed_keys
    if unknown_keys:
        raise InvalidDiscoveryOptionsError(
            "Unsupported discovery option(s): " + ", ".join(sorted(unknown_keys)) + "."
        )

    timeout_ms = payload.get("timeout_ms", DEFAULT_TIMEOUT_MS)
    max_workers = payload.get("max_workers", DEFAULT_WORKERS)
    if isinstance(timeout_ms, bool) or not isinstance(timeout_ms, int):
        raise InvalidDiscoveryOptionsError("timeout_ms must be an integer.")
    if not MIN_TIMEOUT_MS <= timeout_ms <= MAX_TIMEOUT_MS:
        raise InvalidDiscoveryOptionsError(
            f"timeout_ms must be between {MIN_TIMEOUT_MS} and {MAX_TIMEOUT_MS}."
        )
    if isinstance(max_workers, bool) or not isinstance(max_workers, int):
        raise InvalidDiscoveryOptionsError("max_workers must be an integer.")
    if not 1 <= max_workers <= MAX_WORKERS:
        raise InvalidDiscoveryOptionsError(f"max_workers must be between 1 and {MAX_WORKERS}.")
    return timeout_ms, max_workers


def _resolve_hostname(ip_address: str) -> str | None:
    try:
        hostname, _, _ = socket.gethostbyaddr(ip_address)
        hostname = hostname.rstrip(".")
        return hostname or None
    except (OSError, socket.herror):
        return None


def _probe_host(
    ip_address: ipaddress.IPv4Address,
    *,
    timeout_seconds: float,
    ports: tuple[int, ...] = DEFAULT_PORTS,
) -> dict[str, Any] | None:
    """Treat an open or explicitly refused TCP connection as evidence of a live host."""
    started = time.perf_counter()
    active = False
    open_ports: list[int] = []
    response_errors = {errno.ECONNREFUSED, errno.ECONNRESET, errno.ECONNABORTED, 10061}

    for port in ports:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as connection:
                connection.settimeout(timeout_seconds)
                result = connection.connect_ex((str(ip_address), port))
            if result == 0:
                active = True
                open_ports.append(port)
            elif result in response_errors:
                active = True
        except (OSError, TimeoutError):
            continue

    if not active:
        return None

    ip_text = str(ip_address)
    return {
        "ip": ip_text,
        "hostname": _resolve_hostname(ip_text),
        "open_ports": open_ports,
        "latency_ms": round((time.perf_counter() - started) * 1000, 2),
        "discovery_method": "tcp_connect",
    }


def discover_hosts(payload: dict[str, Any]) -> dict[str, Any]:
    """Discover responsive hosts in a validated private IPv4 CIDR range."""
    if not isinstance(payload, dict):
        raise InvalidDiscoveryOptionsError("The discovery payload must be a JSON object.")

    network = _validate_target(payload.get("cidr"))
    timeout_ms, max_workers = _validate_options(payload)
    addresses = list(network.hosts())
    worker_count = min(max_workers, len(addresses))
    started = time.perf_counter()
    discovered: list[dict[str, Any]] = []

    with ThreadPoolExecutor(max_workers=worker_count, thread_name_prefix="netscanner-discovery") as executor:
        futures = {
            executor.submit(
                _probe_host,
                address,
                timeout_seconds=timeout_ms / 1000,
                ports=DEFAULT_PORTS,
            ): address
            for address in addresses
        }
        for future in as_completed(futures):
            host = future.result()
            if host is not None:
                discovered.append(host)

    discovered.sort(key=lambda host: ipaddress.ip_address(host["ip"]))
    duration_ms = round((time.perf_counter() - started) * 1000, 2)
    return {
        "target": str(network),
        "scanned_hosts": len(addresses),
        "active_hosts": len(discovered),
        "duration_ms": duration_ms,
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "method": "tcp_connect",
        "hosts": discovered,
    }
