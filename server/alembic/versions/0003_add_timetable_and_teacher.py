"""add timetable and teacher

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-09 12:45:00.000000

"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = '0003'
down_revision = '0002'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Add teacher_id to sessions table
    op.add_column('sessions', sa.Column('teacher_id', sa.Uuid(), nullable=True))
    op.create_foreign_key('fk_sessions_teacher', 'sessions', 'users', ['teacher_id'], ['id'])

    # Create timetable_entries table
    op.create_table('timetable_entries',
        sa.Column('id', sa.Uuid(), nullable=False),
        sa.Column('teacher_id', sa.Uuid(), nullable=False),
        sa.Column('course_id', sa.String(255), nullable=False),
        sa.Column('room_id', sa.String(255), nullable=False),
        sa.Column('location', sa.String(255), nullable=False),
        sa.Column('day_of_week', sa.String(10), nullable=False),
        sa.Column('start_time', sa.String(5), nullable=False),
        sa.Column('end_time', sa.String(5), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.PrimaryKeyConstraint('id'),
        sa.ForeignKeyConstraint(['teacher_id'], ['users.id']),
    )
    op.create_index(op.f('ix_timetable_entries_teacher_id'), 'timetable_entries', ['teacher_id'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_timetable_entries_teacher_id'), table_name='timetable_entries')
    op.drop_table('timetable_entries')
    op.drop_constraint('fk_sessions_teacher', 'sessions', type_='foreignkey')
    op.drop_column('sessions', 'teacher_id')
