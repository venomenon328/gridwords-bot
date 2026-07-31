# Server bootstrap

On Debian 13: update packages, create a non-root `gridwords` administrator, verify SSH keys, then disable root login and password authentication. Configure the host firewall (and optionally Netcup firewall) to permit only SSH, enable automatic security updates, and install Docker Engine plus the Compose plugin from Docker's documented repository.

Create `/opt/gridwords-bot/{scripts,backups}`; copy the committed Compose and scripts there. Set `runtime.env` to mode `0600`, `backups` to `0700`, and keep both owned by the deployment user. Log in to GHCR interactively with a package token limited to `read:packages`; never put it in a script or shell history.
