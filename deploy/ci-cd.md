# Automatic Contabo deployment

The `deploy` job in `.github/workflows/ci.yml` runs after both backend and
frontend CI jobs succeed on a push to
`phase0/admin-sidebar-and-permission-catalogue`. It sends the deployment
script over SSH, fast-forwards the VPS checkout to the exact tested commit,
rebuilds with `compose.production.yml` and `compose.nginx.yml`, and checks
`/actuator/health`. Pull requests and other branches do not deploy.

## One-time VPS setup

Keep the existing repository at `/home/derick/ADAGS-HMS-V2.0` on the named
branch, with `.env.production` in place. The deploy script refuses to overwrite
uncommitted files. The `derick` account must be able to run Docker without an
interactive sudo password. One option is to add it to the `docker` group:

```sh
sudo usermod -aG docker derick
```

Start a new SSH session, then confirm `docker compose version` and
`docker info` work without `sudo`. Membership in the `docker` group grants
root-level control of the host; limit access to this account and its SSH key.

Create a dedicated SSH key pair for GitHub Actions on your development machine
and authorize only its public key on the VPS. For example:

```sh
ssh-keygen -t ed25519 -f ~/.ssh/adags_ci_deploy -C adags-github-actions
ssh-copy-id -i ~/.ssh/adags_ci_deploy.pub derick@YOUR_VPS_IP
ssh -i ~/.ssh/adags_ci_deploy derick@YOUR_VPS_IP 'whoami'
```

The private key stays on your development machine until you put it in the
GitHub secret below. Do not commit it or copy it into the VPS repository.

## One-time GitHub setup

In repository Settings → Environments, create `production`. Restrict its
deployment branch to `phase0/admin-sidebar-and-permission-catalogue`. To keep
deployments automatic, do not add required reviewers. Add these environment
variables:

| Variable | Value |
| --- | --- |
| `VPS_HOST` | VPS public IP or SSH hostname |
| `VPS_USER` | `derick` |
| `VPS_PORT` | SSH port, if different from `22` |

Add these environment secrets:

| Secret | Value |
| --- | --- |
| `VPS_SSH_KEY` | Full private key from `~/.ssh/adags_ci_deploy` |
| `VPS_KNOWN_HOSTS` | Verified SSH host key line for `VPS_HOST` |

Generate a host key line from your development machine with
`ssh-keyscan -t ed25519 YOUR_VPS_IP`. Before saving it as `VPS_KNOWN_HOSTS`,
compare its `ssh-keygen -lf` fingerprint with the fingerprint of
`/etc/ssh/ssh_host_ed25519_key.pub` on the VPS. For a nonstandard SSH port,
use `ssh-keyscan -p PORT` and set `VPS_PORT` to the same port.

Allow GitHub-hosted runners to reach the VPS SSH port. No database or app
passwords belong in GitHub; `.env.production` remains only on the VPS.

After this setup, push to the named branch. Watch the `Deploy to Contabo` job
in GitHub Actions. If CI fails, deployment does not run. If the deployment
health check fails, inspect its job log and the VPS container logs. The job
does not automatically roll back database migrations.
