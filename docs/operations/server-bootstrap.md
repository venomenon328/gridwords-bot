# Server-Bootstrap unter Debian 13

Diese Anleitung richtet einen leeren Netcup-VPS für den Produktionsbot ein. Platzhalter wie `SERVER_IP` oder `PUBLIC_KEY` werden lokal ersetzt. Passwörter, Tokens und private Schlüssel gehören niemals in Shell-History, Repository, Chat oder Screenshots.

## 1. Debian aktualisieren

Zunächst über die von Netcup bereitgestellten Root-Zugangsdaten anmelden:

```bash
ssh root@SERVER_IP
apt update
apt full-upgrade --yes
apt install --yes sudo ufw unattended-upgrades ca-certificates curl gnupg
hostnamectl set-hostname gridwords-bot
timedatectl set-timezone Europe/Berlin
```

Ein eventuell verlangter Neustart erfolgt vor den weiteren Schritten:

```bash
reboot
```

## 2. Administrativen Benutzer anlegen

```bash
adduser gridwords
usermod --append --groups sudo gridwords
install -d -m 0700 -o gridwords -g gridwords /home/gridwords/.ssh
install -m 0600 -o gridwords -g gridwords /dev/null /home/gridwords/.ssh/authorized_keys
```

Auf dem lokalen Windows-Rechner bei Bedarf einen eigenen Schlüssel erzeugen:

```powershell
ssh-keygen -t ed25519 -a 64 -f $env:USERPROFILE\.ssh\gridwords_netcup
```

Den öffentlichen Schlüssel übertragen:

```powershell
Get-Content $env:USERPROFILE\.ssh\gridwords_netcup.pub |
  ssh root@SERVER_IP "cat >> /home/gridwords/.ssh/authorized_keys && chown gridwords:gridwords /home/gridwords/.ssh/authorized_keys && chmod 600 /home/gridwords/.ssh/authorized_keys"
```

In einem **zweiten Terminal** prüfen, bevor der Root-Zugang eingeschränkt wird:

```powershell
ssh -i $env:USERPROFILE\.ssh\gridwords_netcup gridwords@SERVER_IP "sudo -v && id"
```

## 3. SSH härten

Die ursprüngliche Root-Sitzung bleibt bis zum erfolgreichen Abschlusstest geöffnet.

```bash
cat >/etc/ssh/sshd_config.d/99-gridwords-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
X11Forwarding no
AllowUsers gridwords
EOF

sshd -t
systemctl reload ssh
```

Anschließend erneut in einem neuen Terminal anmelden. Erst wenn dies funktioniert, die ursprüngliche Root-Sitzung schließen.

## 4. Firewall und Sicherheitsupdates

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw --force enable
ufw status verbose

cat >/etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

systemctl enable --now unattended-upgrades.service
systemctl status unattended-upgrades.service --no-pager
```

Eine zusätzliche Netcup-Firewall darf ebenfalls ausschließlich TCP/22 eingehend zulassen. Eine Beschränkung auf eine einzelne Quell-IP ist nur sinnvoll, wenn diese zuverlässig statisch ist.

## 5. Docker Engine installieren

Die folgenden Befehle verwenden das offizielle Docker-APT-Repository:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install --yes docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod --append --groups docker gridwords
```

Die Mitgliedschaft in der Gruppe `docker` gewährt faktisch Root-Rechte und ist hier bewusst auf den dedizierten Deploymentbenutzer begrenzt. Danach einmal ab- und wieder anmelden:

```bash
exit
ssh -i ~/.ssh/gridwords_netcup gridwords@SERVER_IP
docker version
docker compose version
```

Unter Windows wird beim letzten Befehl der Schlüsselpfad entsprechend als `%USERPROFILE%`- beziehungsweise PowerShell-Pfad angegeben.

## 6. Produktionsverzeichnis installieren

Auf dem Server:

```bash
sudo install -d -m 0750 -o gridwords -g gridwords /opt/gridwords-bot
sudo install -d -m 0700 -o gridwords -g gridwords /opt/gridwords-bot/backups
```

Auf dem lokalen Rechner aus dem ausgecheckten Release- beziehungsweise PR-Stand ein Betriebsarchiv erzeugen:

```powershell
tar -czf gridwords-operations.tar.gz compose.production.yaml .env.production.example deployment.env.example scripts ops
scp -i $env:USERPROFILE\.ssh\gridwords_netcup gridwords-operations.tar.gz gridwords@SERVER_IP:/tmp/
```

Auf dem Server installieren:

```bash
cd /opt/gridwords-bot
tar -xzf /tmp/gridwords-operations.tar.gz
rm /tmp/gridwords-operations.tar.gz
chmod 0750 scripts/*.sh
chmod 0600 .env.production.example deployment.env.example
```

`deployment.env` wird beim ersten erfolgreichen Deployment automatisch erzeugt und darf vorher nicht mit einem vermeintlich aktiven Image vorbelegt werden.

## 7. Produktionskonfiguration anlegen

```bash
cd /opt/gridwords-bot
cp .env.production.example runtime.env
chmod 0600 runtime.env
nano runtime.env
```

Jeder Platzhalter wird ersetzt. Insbesondere müssen Datenbankpasswörter übereinstimmen, die JDBC-URL auf `postgres` zeigen und ausschließlich IDs sowie Token der separaten Discord-Produktionsanwendung verwendet werden.

Konfiguration ohne Ausgabe der Secrets validieren:

```bash
grep -E '^(TIME_ZONE|REMINDER_FIRST_TIME|REMINDER_SECOND_TIME|DAILY_CLEANUP_TIME|DISCORD_ENABLED)=' runtime.env
stat -c '%a %U:%G %n' runtime.env backups
```

Erwartet werden `600 gridwords:gridwords` für `runtime.env` und `700 gridwords:gridwords` für `backups`.

## 8. Privat bei GHCR anmelden

Ein klassisches GitHub-PAT mit minimalem `read:packages`-Zugriff verwenden. Das Token wird verdeckt eingelesen und nicht in die History geschrieben:

```bash
read -rsp 'GHCR read token: ' GHCR_TOKEN
echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io --username venomenon328 --password-stdin
unset GHCR_TOKEN
chmod 0600 ~/.docker/config.json
```

## 9. Erstdeployment

Der gewünschte Commit-Tag stammt aus dem erfolgreichen `main`-Workflow:

```bash
cd /opt/gridwords-bot
./scripts/deploy.sh sha-REPLACE_WITH_40_LOWERCASE_HEX_CHARACTERS
./scripts/verify-deployment.sh
```

Danach prüfen:

```bash
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml ps
docker compose --project-name gridwords-production --env-file runtime.env --env-file deployment.env -f compose.production.yaml logs --tail=100 bot
```

PostgreSQL und Managementendpunkte dürfen keine Host-Portfreigabe besitzen.

## 10. Automatische Backups installieren

Erst nach erfolgreichem Erstdeployment:

```bash
sudo install -m 0644 ops/systemd/gridwords-backup.service /etc/systemd/system/gridwords-backup.service
sudo install -m 0644 ops/systemd/gridwords-backup.timer /etc/systemd/system/gridwords-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now gridwords-backup.timer
sudo systemctl start gridwords-backup.service
sudo systemctl status gridwords-backup.service --no-pager
systemctl list-timers gridwords-backup.timer --all
```

Der Timer läuft täglich nach dem Berliner Tageswechsel und führt verpasste Läufe nach einem Serverausfall nach.

## 11. Abschlussprüfung

```bash
sudo ss -lntup
sudo ufw status verbose
docker ps
/opt/gridwords-bot/scripts/verify-deployment.sh
ls -lah /opt/gridwords-bot/backups
systemctl list-timers gridwords-backup.timer --all
```

Öffentlich lauschen darf nur SSH. Anschließend folgen die reale Discord-Abnahme, ein Serverneustart sowie der dokumentierte Backup-/Restore- und App-Rollback-Test.
