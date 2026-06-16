"""add sessions table

Revision ID: 0002
Revises: 0001_initial
Create Date: 2026-06-16 14:30:00.000000

"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = '0002'
down_revision = '0001_initial'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create sessions table (db-agnostic: use String for status instead of PG enum)
    op.create_table('sessions',
        sa.Column('id', sa.Uuid(), nullable=False),
        sa.Column('course_id', sa.String(255), nullable=False),
        sa.Column('room_id', sa.String(255), nullable=False),
        sa.Column('location', sa.String(255), nullable=False),
        sa.Column('scheduled_start', sa.DateTime(timezone=True), nullable=False),
        sa.Column('scheduled_end', sa.DateTime(timezone=True), nullable=False),
        sa.Column('actual_start', sa.DateTime(timezone=True), nullable=True),
        sa.Column('actual_end', sa.DateTime(timezone=True), nullable=True),
        sa.Column('status', sa.String(20), nullable=False, server_default='SCHEDULED'),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_sessions_course_id'), 'sessions', ['course_id'], unique=False)
    op.create_index(op.f('ix_sessions_status'), 'sessions', ['status'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_sessions_status'), table_name='sessions')
    op.drop_index(op.f('ix_sessions_course_id'), table_name='sessions')
    op.drop_table('sessions')
