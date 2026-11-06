from netscanner.protocol import PROTOCOL_VERSION, handle_request


def request(**overrides):
    payload = {
        "protocol_version": PROTOCOL_VERSION,
        "request_id": "test-request-001",
        "action": "system.ping",
        "payload": {},
    }
    payload.update(overrides)
    return payload


def test_system_ping_returns_engine_metadata():
    response = handle_request(request())

    assert response["success"] is True
    assert response["request_id"] == "test-request-001"
    assert response["data"]["status"] == "ready"
    assert "system.ping" in response["data"]["capabilities"]
    assert response["error"] is None


def test_unknown_action_returns_structured_error():
    response = handle_request(request(action="network.scan"))

    assert response["success"] is False
    assert response["error"]["code"] == "UNKNOWN_ACTION"
    assert response["request_id"] == "test-request-001"


def test_unsupported_protocol_returns_structured_error():
    response = handle_request(request(protocol_version="99.0"))

    assert response["success"] is False
    assert response["error"]["code"] == "UNSUPPORTED_PROTOCOL"


def test_non_object_request_is_rejected():
    response = handle_request(["not", "an", "object"])

    assert response["success"] is False
    assert response["error"]["code"] == "INVALID_REQUEST"


def test_payload_must_be_an_object():
    response = handle_request(request(payload=["bad", "payload"]))

    assert response["success"] is False
    assert response["error"]["code"] == "INVALID_PAYLOAD"
