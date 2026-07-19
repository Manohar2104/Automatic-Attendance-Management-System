from __future__ import annotations

from pathlib import Path


DASHBOARD = Path(__file__).resolve().parents[2] / "app" / "static" / "dashboard.html"


def test_dashboard_fetches_expected_ble_endpoints_with_bearer_auth():
    content = DASHBOARD.read_text(encoding="utf-8")

    assert "/api/ble/sessions" in content
    assert "/api/ble/session/${selectedSessionId}/summary" in content
    assert "/api/ble/session/${selectedSessionId}/observations" in content
    assert "/api/ble/session/${selectedSessionId}/presence" in content
    assert "'Authorization': `Bearer ${currentToken}`" in content
    assert "accept: 'application/json'" not in content.lower()


def test_dashboard_expects_fields_present_in_backend_ble_responses():
    content = DASHBOARD.read_text(encoding="utf-8")

    expected_fragments = [
        "payload.sessions",
        "payload.session.session_id",
        "payload.session.course_id",
        "payload.session.teacher_id",
        "payload.session.status",
        "payload.session.start_time",
        "payload.session.end_time",
        "payload.session.attendance_mode",
        "payload.registered_students",
        "payload.detected_students",
        "payload.present",
        "payload.missing",
        "payload.absent",
        "payload.late",
        "payload.latest_observation",
        "payload.average_rssi",
        "payload.observations",
        "item.student_id",
        "item.student_name",
        "item.anonymous_ble_id",
        "item.rssi",
        "item.last_seen",
        "item.advertisement_count",
        "item.observation_timestamp",
        "payload.records",
        "record.student_id",
        "record.student_name",
        "record.status",
        "record.last_seen",
        "payload.summary?.present",
        "payload.summary?.missing",
        "payload.summary?.absent",
        "payload.summary?.late",
    ]

    for fragment in expected_fragments:
        assert fragment in content


def test_dashboard_contract_has_no_ble_api_mismatch_keywords():
    content = DASHBOARD.read_text(encoding="utf-8")

    forbidden_fragments = [
        "compute-attendance",
        "override-attendance",
        "payload.hmac",
    ]

    for fragment in forbidden_fragments:
        assert fragment not in content
