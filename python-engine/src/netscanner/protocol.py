"""Request/response protocol shared with the Java application."""

from __future__ import annotations

import platform
from typing import Any

from . import __version__

PROTOCOL_VERSION = "1.0"
MAX_REQUEST_BYTES = 1_048_576
SUPPORTED_ACTIONS = {"system.ping"}


def make_response(
    *,
    request_id: str | None,
    success: bool,
    data: dict[str, Any] | None = None,
    error: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Build a response with the stable envelope expected by Java."""
    return {
        "protocol_version": PROTOCOL_VERSION,
        "request_id": request_id,
        "success": success,
        "data": data,
        "error": error,
    }


def handle_request(request: Any) -> dict[str, Any]:
    """Validate and handle one request without writing to stdout."""
    if not isinstance(request, dict):
        return make_response(
            request_id=None,
            success=False,
            error={"code": "INVALID_REQUEST", "message": "The request must be a JSON object."},
        )

    request_id = request.get("request_id")
    if not isinstance(request_id, str) or not request_id.strip() or len(request_id) > 128:
        request_id = None
        return make_response(
            request_id=request_id,
            success=False,
            error={"code": "INVALID_REQUEST", "message": "A non-empty request_id of at most 128 characters is required."},
        )

    if request.get("protocol_version") != PROTOCOL_VERSION:
        return make_response(
            request_id=request_id,
            success=False,
            error={"code": "UNSUPPORTED_PROTOCOL", "message": f"Supported protocol version: {PROTOCOL_VERSION}."},
        )

    action = request.get("action")
    payload = request.get("payload", {})
    if not isinstance(payload, dict):
        return make_response(
            request_id=request_id,
            success=False,
            error={"code": "INVALID_PAYLOAD", "message": "The payload must be a JSON object."},
        )

    if action == "system.ping":
        return make_response(
            request_id=request_id,
            success=True,
            data={
                "status": "ready",
                "engine": "NetScanner Python Engine",
                "engine_version": __version__,
                "python_version": platform.python_version(),
                "capabilities": sorted(SUPPORTED_ACTIONS),
            },
        )

    return make_response(
        request_id=request_id,
        success=False,
        error={"code": "UNKNOWN_ACTION", "message": f"Unsupported action: {action!r}."},
    )
