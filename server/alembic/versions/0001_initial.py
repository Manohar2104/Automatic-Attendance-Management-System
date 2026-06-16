"""initial

Revision ID: 0001_initial
Revises: 
Create Date: 2026-06-16
"""
from alembic import op
import sqlalchemy as sa

revision = '0001_initial'
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        'users',
        sa.Column('id', sa.Uuid(), primary_key=True),
        sa.Column('email', sa.String(255), nullable=False, unique=True),
        sa.Column('password_hash', sa.String(255), nullable=False),
        sa.Column('role', sa.String(20), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
    )

    op.create_table(
        'device_bindings',
        sa.Column('id', sa.Uuid(), primary_key=True),
        sa.Column('user_id', sa.Uuid(), sa.ForeignKey('users.id'), nullable=True),
        sa.Column('device_fingerprint', sa.String(255), nullable=False, unique=True),
        sa.Column('status', sa.String(20), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('last_seen_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('revoked_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('revoked_by', sa.Uuid(), sa.ForeignKey('users.id'), nullable=True),
    )

    op.create_table(
        'attendances',
        sa.Column('id', sa.Uuid(), primary_key=True),
        sa.Column('student_id', sa.Uuid(), sa.ForeignKey('users.id'), nullable=False),
        sa.Column('session_id', sa.String(255), nullable=False),
        sa.Column('score', sa.Float, nullable=True),
        sa.Column('status', sa.String(20), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
    )

    op.create_table(
        'events',
        sa.Column('id', sa.Uuid(), primary_key=True),
        sa.Column('user_id', sa.Uuid(), sa.ForeignKey('users.id'), nullable=True),
        sa.Column('session_id', sa.String(255), nullable=True),
        sa.Column('type', sa.String(20), nullable=False),
        sa.Column('location', sa.String(255), nullable=True),
        sa.Column('timestamp', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
    )

    op.create_table(
        'attendance_overrides',
        sa.Column('id', sa.Uuid(), primary_key=True),
        sa.Column('attendance_id', sa.Uuid(), sa.ForeignKey('attendances.id'), nullable=False),
        sa.Column('admin_id', sa.Uuid(), sa.ForeignKey('users.id'), nullable=False),
        sa.Column('original_status', sa.String(20), nullable=False),
        sa.Column('override_status', sa.String(20), nullable=False),
        sa.Column('justification', sa.String(1024), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
    )


def downgrade():
    op.drop_table('attendance_overrides')
    op.drop_table('events')
    op.drop_table('attendances')
    op.drop_table('device_bindings')
    op.drop_table('users')
