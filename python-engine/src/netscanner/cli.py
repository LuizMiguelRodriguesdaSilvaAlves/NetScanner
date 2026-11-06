"""Command-line entry point for the NetScanner Python engine."""

from __future__ import annotations

import json
import sys
from typing import Any

from .protocol import MAX_REQUEST_BYTES, handle_request, make_response


def _read_request() -> Any:
    raw = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 1)
    if len(raw) > MAX_REQUEST_BYTES:
        raise ValueError("Request exceeds the 1 MiB size limit.")
    if not raw:
        raise ValueError("No request was received on standard input.")
    return json.loads(raw.decode("utf-8"))


def main() -> int:
    """Read one JSON request from stdin and write exactly one JSON response."""
    try:
        request = _read_request()
        response = handle_request(request)
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        response = make_response(
            request_id=None,
            success=False,
            error={"code": "INVALID_REQUEST", "message": str(exc)},
        )

    # stdout is a machine-readable channel: never print logs or banners here.
    sys.stdout.write(json.dumps(response, separators=(",", ":"), ensure_ascii=False))
    sys.stdout.write("\n")
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
