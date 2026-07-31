# Backup and restore

`backup.sh` writes and validates PostgreSQL custom dumps atomically, retaining 14 daily and 8 weekly generations. Schedule it with a systemd timer after Berlin midnight and outside reminder times.

```bash
/opt/gridwords-bot/scripts/backup.sh
docker compose -f /opt/gridwords-bot/compose.production.yaml stop bot
/opt/gridwords-bot/scripts/restore.sh --force /opt/gridwords-bot/backups/gridwords-YYYYMMDDTHHMMSSZ.dump
```

Restore refuses production overwrite without `--force`, requires the bot stopped, makes a safety backup, validates the dump, then restarts and verifies the bot. Test a dump first with `restore.sh --to-empty-database gridwords_restore_test DUMP`.
