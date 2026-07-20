"""add ble session packet counts

Revision ID: 0004_add_ble_session_packet_counts
Revises: 0003_ble_attendance_module
Create Date: 2026-07-20 00:00:00.000000
"""

from alembic import op
import sqlalchemy as sa


revision = "0004_add_ble_session_packet_counts"
down_revision = "0003_ble_attendance_module"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "ble_sessions",
        sa.Column("packets_received", sa.Integer(), nullable=False, server_default=sa.text("0")),
    )
    op.alter_column("ble_sessions", "packets_received", server_default=None)


def downgrade() -> None:
    op.drop_column("ble_sessions", "packets_received")