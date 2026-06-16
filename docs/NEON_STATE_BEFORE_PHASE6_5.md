Environment:
- Friend production database (untouched)
- Isolated Neon project created

Initial Findings:
- Friend schema differed from canonical schema.
- No schema_migrations table existed.

Decision:
- Use isolated Neon database for activation.