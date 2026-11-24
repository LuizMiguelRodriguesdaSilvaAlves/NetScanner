import pytest

from netscanner.service_detection import get_service_hint


@pytest.mark.parametrize(
    ("port", "expected"),
    [
        (21, "FTP"),
        (22, "SSH"),
        (53, "DNS"),
        (80, "HTTP"),
        (443, "HTTPS"),
        (445, "SMB"),
        (3306, "MySQL"),
        (3389, "RDP"),
        (5432, "PostgreSQL"),
        (6379, "Redis"),
    ],
)
def test_known_tcp_ports_return_service_hints(port, expected):
    assert get_service_hint(port) == expected


@pytest.mark.parametrize("port", [1, 444, 12345, 65535])
def test_unknown_tcp_ports_return_no_service_hint(port):
    assert get_service_hint(port) is None


@pytest.mark.parametrize("port", [True, False, 0, -1, 65536, "22", None])
def test_invalid_port_values_return_no_service_hint(port):
    assert get_service_hint(port) is None
