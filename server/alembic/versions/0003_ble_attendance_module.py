"""ble attendance module

Revision ID: 0003_ble_attendance_module
Revises: 0002
Create Date: 2026-07-10 00:00:00.000000
"""
from alembic import op
import sqlalchemy as sa


revision = "0003_ble_attendance_module"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "ble_sessions",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("course_id", sa.String(length=255), nullable=False),
        sa.Column("teacher_id", sa.Uuid(), nullable=False),
        sa.Column("attendance_mode", sa.String(length=20), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column("start_time", sa.DateTime(timezone=True), nullable=False),
        sa.Column("end_time", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "attendance_mode IN ('WIFI', 'BLE')",
            name="ck_ble_sessions_attendance_mode",
        ),
        sa.CheckConstraint(
            "status IN ('ACTIVE', 'ENDED', 'CANCELLED')",
            name="ck_ble_sessions_status",
        ),
        sa.ForeignKeyConstraint(["teacher_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        op.f("ix_ble_sessions_course_id_start_time"),
        "ble_sessions",
        ["course_id", "start_time"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_sessions_teacher_id_start_time"),
        "ble_sessions",
        ["teacher_id", "start_time"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_sessions_status_start_time"),
        "ble_sessions",
        ["status", "start_time"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_sessions_attendance_mode_status"),
        "ble_sessions",
        ["attendance_mode", "status"],
        unique=False,
    )

    op.create_table(
        "ble_registered_devices",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("student_id", sa.Uuid(), nullable=False),
        sa.Column("anonymous_ble_id", sa.String(length=64), nullable=False),
        sa.Column("public_identifier", sa.String(length=255), nullable=False),
        sa.Column("device_hash", sa.String(length=255), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["student_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "anonymous_ble_id", name="uq_ble_registered_devices_anonymous_ble_id"
        ),
        sa.UniqueConstraint("device_hash", name="uq_ble_registered_devices_device_hash"),
        sa.UniqueConstraint("student_id", name="uq_ble_registered_devices_student_id"),
    )
    op.create_index(
        op.f("ix_ble_registered_devices_student_id"),
        "ble_registered_devices",
        ["student_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_registered_devices_public_identifier"),
        "ble_registered_devices",
        ["public_identifier"],
        unique=False,
    )

    op.create_table(
        "ble_observations",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("session_id", sa.Uuid(), nullable=False),
        sa.Column("student_id", sa.Uuid(), nullable=False),
        sa.Column("rssi", sa.Integer(), nullable=False),
        sa.Column("rolling_token", sa.String(length=64), nullable=False),
        sa.Column("timestamp", sa.DateTime(timezone=True), nullable=False),
        sa.Column("hmac", sa.String(length=128), nullable=False),
        sa.Column("last_seen", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.CheckConstraint("rssi BETWEEN -120 AND 0", name="ck_ble_observations_rssi_range"),
        sa.ForeignKeyConstraint(["session_id"], ["ble_sessions.id"]),
        sa.ForeignKeyConstraint(["student_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "session_id",
            "student_id",
            "rolling_token",
            "timestamp",
            name="uq_ble_observations_session_student_token_timestamp",
        ),
    )
    op.create_index(
        op.f("ix_ble_observations_session_id_timestamp"),
        "ble_observations",
        ["session_id", "timestamp"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_observations_session_id_student_id"),
        "ble_observations",
        ["session_id", "student_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_observations_student_id_last_seen"),
        "ble_observations",
        ["student_id", "last_seen"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_observations_rolling_token"),
        "ble_observations",
        ["rolling_token"],
        unique=False,
    )

    op.create_table(
        "ble_attendance",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("session_id", sa.Uuid(), nullable=False),
        sa.Column("student_id", sa.Uuid(), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column("first_seen", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_seen", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("CURRENT_TIMESTAMP"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "status IN ('PRESENT', 'MISSING')",
            name="ck_ble_attendance_status",
        ),
        sa.ForeignKeyConstraint(["session_id"], ["ble_sessions.id"]),
        sa.ForeignKeyConstraint(["student_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "session_id", "student_id", name="uq_ble_attendance_session_student"
        ),
    )
    op.create_index(
        op.f("ix_ble_attendance_session_id_status"),
        "ble_attendance",
        ["session_id", "status"],
        unique=False,
    )
    op.create_index(
        op.f("ix_ble_attendance_student_id_last_seen"),
        "ble_attendance",
        ["student_id", "last_seen"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(op.f("ix_ble_attendance_student_id_last_seen"), table_name="ble_attendance")
    op.drop_index(op.f("ix_ble_attendance_session_id_status"), table_name="ble_attendance")
    op.drop_table("ble_attendance")

    op.drop_index(op.f("ix_ble_observations_rolling_token"), table_name="ble_observations")
    op.drop_index(op.f("ix_ble_observations_student_id_last_seen"), table_name="ble_observations")
    op.drop_index(op.f("ix_ble_observations_session_id_student_id"), table_name="ble_observations")
    op.drop_index(op.f("ix_ble_observations_session_id_timestamp"), table_name="ble_observations")
    op.drop_table("ble_observations")

    op.drop_index(op.f("ix_ble_registered_devices_public_identifier"), table_name="ble_registered_devices")
    op.drop_index(op.f("ix_ble_registered_devices_student_id"), table_name="ble_registered_devices")
    op.drop_table("ble_registered_devices")

    op.drop_index(op.f("ix_ble_sessions_attendance_mode_status"), table_name="ble_sessions")
    op.drop_index(op.f("ix_ble_sessions_status_start_time"), table_name="ble_sessions")
    op.drop_index(op.f("ix_ble_sessions_teacher_id_start_time"), table_name="ble_sessions")
    op.drop_index(op.f("ix_ble_sessions_course_id_start_time"), table_name="ble_sessions")
    op.drop_table("ble_sessions")
