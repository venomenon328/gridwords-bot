# Deployment

Copy `.env.production.example` to `/opt/gridwords-bot/runtime.env` and `deployment.env.example` to `deployment.env`; replace every placeholder with production-only values. Use only `sha-<40 hex>` or `MAJOR.MINOR.PATCH` tags.

```bash
/opt/gridwords-bot/scripts/deploy.sh 0.9.0
/opt/gridwords-bot/scripts/verify-deployment.sh
```

The deploy script backs up first, records image ID and prior image, waits for health, and restores the prior app image on an unsuccessful update. Repeat the same tag for an idempotent verification-only success. Roll back the app by deploying its earlier explicit tag; use database restore separately.
