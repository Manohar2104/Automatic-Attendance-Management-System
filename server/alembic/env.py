import os
from sqlalchemy import engine_from_config, pool
from alembic import context

# Avoid strict logging config parsing; not required for this env
config = context.config

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from app.db import Base
from app.config import settings


target_metadata = Base.metadata


def run_migrations_offline():
    url = settings.database_url
    context.configure(url=url, target_metadata=target_metadata, literal_binds=True)
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online():
    configuration = config.get_section(config.config_ini_section) or {}
    # Alembic expects a sync DB URL. Convert common async URLs to their sync counterparts.
    url = settings.database_url
    sync_url = url
    # handle async dialect suffixes
    if "+aiosqlite" in url:
        sync_url = url.replace('+aiosqlite', '')
    if "+asyncpg" in url:
        # prefer psycopg sync driver for alembic
        sync_url = url.replace('+asyncpg', '+psycopg')
    # if a plain postgresql:// URL was provided, prefer psycopg driver
    if sync_url.startswith('postgresql://') and '+psycopg' not in sync_url and '+psycopg2' not in sync_url:
        sync_url = sync_url.replace('postgresql://', 'postgresql+psycopg://')
    configuration['sqlalchemy.url'] = sync_url
    connectable = engine_from_config(
        configuration,
        prefix='sqlalchemy.',
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
