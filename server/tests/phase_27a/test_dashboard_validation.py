from __future__ import annotations

from pathlib import Path


DASHBOARD = Path(__file__).resolve().parents[2] / "app" / "static" / "dashboard.html"


def test_dashboard_uses_only_ble_read_endpoints():
    content = DASHBOARD.read_text(encoding="utf-8")

    assert "/api/ble/sessions" in content
    assert "/api/ble/session/${selectedSessionId}/summary" in content
    assert "/api/ble/session/${selectedSessionId}/observations" in content
    assert "/api/ble/session/${selectedSessionId}/presence" in content
    assert "compute-attendance" not in content
    assert "override-attendance" not in content


def test_dashboard_exposes_required_render_states_and_auth_flow():
    content = DASHBOARD.read_text(encoding="utf-8")

    for symbol in [
        "handleLogin",
        "logout",
        "renderSessions",
        "renderSessionSummary",
        "renderObservations",
        "renderPresence",
        "renderLoading",
        "renderEmpty",
        "renderError",
        "Bearer ${currentToken}",
    ]:
        assert symbol in content


def test_dashboard_refresh_defaults_to_ten_seconds():
    content = DASHBOARD.read_text(encoding="utf-8")

    assert 'value="10"' in content
    assert 'min="5"' in content
    assert 'max="120"' in content