"""Conservative service hints based on conventional TCP port assignments.

These hints are not protocol detection, banner grabbing, or version fingerprinting.
"""

from __future__ import annotations

# Common TCP ports. The mapping is intentionally descriptive, not exhaustive.
_SERVICE_HINTS: dict[int, str] = {
    20: "FTP data",
    21: "FTP",
    22: "SSH",
    23: "Telnet",
    25: "SMTP",
    53: "DNS",
    80: "HTTP",
    110: "POP3",
    111: "ONC RPC",
    119: "NNTP",
    135: "Microsoft RPC",
    139: "NetBIOS Session Service",
    143: "IMAP",
    179: "BGP",
    389: "LDAP",
    443: "HTTPS",
    445: "SMB",
    465: "SMTPS",
    587: "SMTP Submission",
    631: "IPP",
    636: "LDAPS",
    873: "rsync",
    990: "FTPS",
    993: "IMAPS",
    995: "POP3S",
    1433: "Microsoft SQL Server",
    1521: "Oracle Database",
    2049: "NFS",
    2375: "Docker API",
    2376: "Docker API (TLS)",
    3000: "HTTP alternate",
    3306: "MySQL",
    3389: "RDP",
    5432: "PostgreSQL",
    5672: "AMQP",
    5900: "VNC",
    5985: "WinRM (HTTP)",
    5986: "WinRM (HTTPS)",
    6379: "Redis",
    6443: "Kubernetes API",
    8000: "HTTP alternate",
    8080: "HTTP alternate",
    8443: "HTTPS alternate",
    9200: "Elasticsearch HTTP API",
    9300: "Elasticsearch transport",
    27017: "MongoDB",
}


def get_service_hint(port: int) -> str | None:
    """Return a conventional TCP service hint for a port, if one is known."""
    if isinstance(port, bool) or not isinstance(port, int) or not 1 <= port <= 65535:
        return None
    return _SERVICE_HINTS.get(port)
