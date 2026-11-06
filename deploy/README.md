# Deploy ADAGS HMS on an Ubuntu VPS

This setup runs the React frontend, Java backend, PostgreSQL, Redis, RustFS,
and Caddy. Only Caddy publishes ports. Caddy gets and renews HTTPS certificates
for the app and file-download domains.

## 1. DNS and host prerequisites

Create two DNS `A` records pointing to the VPS public IPv4 address:

| Name | Example hostname |
| --- | --- |
| `adagshms` | `adagshms.derutech.com` |
| `files.adagshms` | `files.adagshms.derutech.com` |

If the VPS also has a public IPv6 address, add matching `AAAA` records only
when IPv6 traffic reaches this VPS. Open inbound TCP 80 and 443, and UDP 443
if desired for HTTP/3. Keep 5432, 6379, 9000, 9001, and 8080 closed.
The server must have Docker Engine with the Compose plugin installed. Follow
the [official Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/).
No separate Java, Node, or Nginx installation is needed on the VPS.

## 2. Fetch the intended code

Clone the repository, or update the existing checkout. Select the branch
that contains the changes you intend to deploy. Local uncommitted changes on
a development computer are not present in a VPS clone.

```bash
git clone git@github.com:derick328/ADAGS-HMS-V2.0.git
cd ADAGS-HMS-V2.0
git branch --show-current
```

For a private repository, give the VPS a read-only GitHub deploy key. Do not
copy a personal SSH private key onto the VPS.

## 3. Set secrets

```bash
cp .env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

Set distinct, strong values for `DB_PASSWORD`, `MINIO_ROOT_PASSWORD`, and
`ADAGS_BOOTSTRAP_ADMIN_PASSWORD`. Set `MINIO_ROOT_USER` to a non-default
identifier. Keep the `MINIO_ROOT_*` names in the environment file: Compose
passes them to RustFS and to the Java application's S3 client. Do not commit
`.env.production`; it is ignored by Git. The bootstrap password is required
on the first database migration and should be changed after the first login.

## 4. Build and start

```bash
sudo docker compose --env-file .env.production -f compose.production.yml config --quiet
sudo docker compose --env-file .env.production -f compose.production.yml up -d --build
sudo docker compose --env-file .env.production -f compose.production.yml ps
sudo docker compose --env-file .env.production -f compose.production.yml logs --tail=100 backend caddy
```

Wait for Caddy to obtain HTTPS certificates. Check the public health endpoint:

```bash
curl -fsS https://adagshms.derutech.com/actuator/health
```

Then open `https://adagshms.derutech.com`, log in with the bootstrap admin,
and change its password. Verify a lab attachment download too: it exercises
the separate file hostname and signed S3 URL.

## Updates and backups

Before an update, back up PostgreSQL and the storage volume together. The
database holds application records; RustFS holds uploaded attachments. Redis
stores sessions, so a Redis loss signs users out. Caddy's volume stores TLS
certificates. Keep backups outside this VPS and test a restore.

```bash
git pull --ff-only
sudo docker compose --env-file .env.production -f compose.production.yml up -d --build
sudo docker compose --env-file .env.production -f compose.production.yml logs --tail=100 backend caddy
```

Flyway runs database migrations when the backend starts. Check migration logs
before returning the app to clinical use. Never run `docker compose down -v`:
the `-v` option deletes the database and object-storage volumes.

If an older deployment already contains MinIO data, do not mount that volume
as RustFS storage. Migrate its objects through the S3 API before switching.
