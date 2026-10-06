# Deploying to an Oracle Cloud Always Free VM

The whole stack runs on one free ARM server with [docker-compose.prod.yml](../docker-compose.prod.yml):

```
Internet ──HTTPS──▶ Caddy :443 ──▶ nginx (frontend) ──/api/──▶ Spring Boot ──▶ Postgres
                    (Let's Encrypt)   static React app            (internal)      (internal)
```

Only Caddy has public ports. It gets and renews the HTTPS certificate automatically. Everything
else is reachable only on the internal Docker network.

Commands below run **on the server** unless they say otherwise.

---

## 1. Create the VM (OCI console)

**Compute → Instances → Create instance**

- **Image:** Canonical Ubuntu 24.04 (pick the `aarch64` build under "Change image").
- **Shape:** Ampere → `VM.Standard.A1.Flex`, **1 OCPU, 6 GB memory**. That's plenty for this app
  and leaves half of the Always Free pool (2 OCPU / 12 GB in total) unused.
- **Networking:** create a new VCN with a public subnet, and keep "Assign a public IPv4 address" on.
- **SSH keys:** upload your public key. To make one on Windows: `ssh-keygen -t ed25519` in a terminal,
  then upload `C:\Users\<you>\.ssh\id_ed25519.pub`.
- Boot volume: the default (~47 GB) is fine.

"Out of capacity" errors are common for A1 shapes. Try another availability domain, or try again later.

If creating the instance fails, the form has usually created the VCN already. Next time, choose
**Select existing virtual cloud network** instead: each attempt with "Create new" makes another VCN,
and free accounts can only have a couple (`vcn-count` limit).

### Retrying automatically

[oci_launch_retry.py](oci_launch_retry.py) asks for the instance every 2 minutes, in every
availability domain, until there's capacity. It reuses the existing `splitledger-subnet`, picks the
newest Ubuntu 24.04 image for the A1 shape, and stops once the instance exists.

1. Install the OCI CLI (it includes the Python SDK the script uses): `python -m pip install --user oci-cli`
2. Create an API key: in the console, open your **profile** (top right) → **API keys** (under
   *Tokens and keys*) → **Add API key** → **Generate API key pair** → **Download private key** →
   **Add**. Save the key as `C:\Users\<you>\.oci\oci_api_key.pem`.
3. Copy the **Configuration file preview** that the console shows into `C:\Users\<you>\.oci\config`
   (no file extension), and change its `key_file=` line to the path of the `.pem` file.
4. Check the setup without launching anything, then start retrying:
   ```
   python deploy/oci_launch_retry.py --check
   python deploy/oci_launch_retry.py
   ```

Keep the PC awake while it runs (it pauses if Windows goes to sleep). When it succeeds it prints the
public IP and shows a pop-up.

Note the instance's **public IP**, then connect:

```bash
ssh ubuntu@<public-ip>          # run on your own computer
```

## 2. Open ports 80 and 443

There are two firewalls, and both need the ports open.

**a) Oracle's network firewall (OCI console):** Networking → Virtual cloud networks → your VCN →
the public subnet → its security list → **Add ingress rules**:

| Source CIDR | Protocol | Destination port |
|---|---|---|
| `0.0.0.0/0` | TCP | 80 |
| `0.0.0.0/0` | TCP | 443 |
| `0.0.0.0/0` | UDP | 443 (optional, HTTP/3) |

**b) The VM's own firewall:** Oracle's Ubuntu images ship iptables rules that reject everything
except SSH. Insert accept rules *before* the final `REJECT` rule:

```bash
sudo iptables -L INPUT --line-numbers      # find the line number of the REJECT rule, e.g. 5
sudo iptables -I INPUT 5 -p tcp --dport 80 -m state --state NEW -j ACCEPT
sudo iptables -I INPUT 5 -p tcp --dport 443 -m state --state NEW -j ACCEPT
sudo iptables -I INPUT 5 -p udp --dport 443 -m state --state NEW -j ACCEPT
sudo netfilter-persistent save             # keep the rules after a reboot
```

## 3. Point a domain at the VM

Caddy needs a domain name to get a certificate for. [DuckDNS](https://www.duckdns.org) is free:
sign in, create a subdomain (e.g. `splitledger` → `splitledger.duckdns.org`), and set its IP to the
VM's public IP. Check it from your computer with `nslookup splitledger.duckdns.org`.

## 4. Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit                                         # log out and SSH back in so the group change applies
docker run --rm hello-world                  # check it works without sudo
```

## 5. Get the code and configure it

```bash
git clone https://github.com/<you>/splitledger.git
cd splitledger
cp .env.example .env
chmod 600 .env
nano .env
```

Set **every** value; the production compose file refuses to start if a secret is missing:

```bash
DB_USERNAME=splitledger
DB_PASSWORD=<openssl rand -hex 24>
JWT_SECRET=<openssl rand -base64 48>
ADMIN_EMAIL=<your email>
ADMIN_PASSWORD=<a strong password>
SITE_ADDRESS=splitledger.duckdns.org
```

(`COOKIE_SECURE` doesn't matter here: the production compose file always sets it to `true`.)

`DB_PASSWORD` is only applied when the database is first created. To change it later, change it in
Postgres too (`ALTER USER splitledger PASSWORD '…'`).

## 6. Start it

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f caddy backend     # Ctrl+C to stop following
```

The first build compiles the backend and frontend on the VM and takes several minutes. When the
backend logs `Started SplitLedgerApplication` and Caddy logs `certificate obtained successfully`,
open `https://<SITE_ADDRESS>` and sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

## 7. Back up the database

```bash
mkdir -p ~/backups
crontab -e
# add this line: daily dump at 03:00, keeps the newest 14
0 3 * * * cd ~/splitledger && bash deploy/backup.sh >> ~/backups/backup.log 2>&1
```

Backups on the same VM don't survive losing the VM, so copy one off now and then (on your computer):
`scp ubuntu@<public-ip>:~/backups/<file>.sql.gz .`

To restore a dump into a **fresh, empty** database:

```bash
gunzip -c ~/backups/<file>.sql.gz | docker compose -f docker-compose.prod.yml exec -T db \
    psql -U splitledger -d splitledger
```

## Updating

```bash
cd ~/splitledger
git pull
docker compose -f docker-compose.prod.yml up -d --build    # Flyway applies new migrations on startup
docker image prune -f                                      # remove the old images
```

## Good to know

- **Idle reclaim:** Oracle may reclaim Always Free instances that stay almost completely idle
  (very low CPU, network and memory use) for 7 days. Check Oracle's current Always Free policy.
  Upgrading the account to Pay As You Go stops reclaiming, and Always Free resources stay free,
  but then anything beyond the free limits can be billed.
- **Security updates:** Ubuntu installs security updates automatically (`unattended-upgrades`).
  Reboot now and then (`sudo reboot`); the containers come back up by themselves.
- **Let's Encrypt rate limits:** certificates are stored in the `caddy_data` volume. Don't delete
  it repeatedly, or Let's Encrypt will temporarily refuse new certificates for the domain.
- **Swagger UI isn't public:** nginx only forwards `/api/`, so `/swagger-ui.html` stays internal.

## Troubleshooting

| Symptom | Check |
|---|---|
| Browser can't connect at all | Both firewalls (step 2); `docker compose -f docker-compose.prod.yml ps` shows `caddy` up |
| Caddy logs certificate errors | The domain resolves to the VM's IP (`nslookup`); port 80 is reachable (Let's Encrypt checks it) |
| `502` from the site | The backend is still starting or crashed: `docker compose -f docker-compose.prod.yml logs backend` |
| Backend restarts repeatedly | Memory: `docker stats`. The backend container is capped at 1 GB (`mem_limit`). |
| `required variable … is missing a value` | A setting is missing from `.env` (step 5) |
