# Troubleshooting

Use `docker compose -f compose.production.yaml ps`, `logs --tail=200 bot`, and `scripts/verify-deployment.sh`. Never paste environment dumps, tokens, passwords, or signed Discord URLs into tickets.

If PostgreSQL is unhealthy, stop before restoring; do not delete the volume as a repair step. If a bot update is unhealthy, deploy the previous explicit image tag. A complete test reset is `docker compose --project-name gridwords-test -f compose.production.yaml down -v`; never use this command for production.
