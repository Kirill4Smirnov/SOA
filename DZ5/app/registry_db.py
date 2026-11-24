from app.common import pg


if __name__ == '__main__':
    with pg() as conn:
        conn.autocommit = True
        if not conn.execute("SELECT 1 FROM pg_database WHERE datname='schema_registry'").fetchone():
            conn.execute('CREATE DATABASE schema_registry')
