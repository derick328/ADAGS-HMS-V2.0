#!/usr/bin/env bash
set -euo pipefail

expected_sha=${1:?Pass the tested Git commit SHA}
if [[ ! $expected_sha =~ ^[0-9a-f]{40}$ ]]; then
    echo "Invalid Git commit SHA" >&2
    exit 1
fi

repo_dir=/home/derick/ADAGS-HMS-V2.0
deploy_branch=phase0/admin-sidebar-and-permission-catalogue
app_domain=adagshms.derutech.com

cd "$repo_dir"
mkdir -p "$HOME/.local/state/adags"
exec 9>"$HOME/.local/state/adags/deploy.lock"
flock 9

if [[ $(git branch --show-current) != "$deploy_branch" ]]; then
    echo "VPS checkout is not on $deploy_branch" >&2
    exit 1
fi
if [[ -n $(git status --porcelain) ]]; then
    echo "VPS checkout has uncommitted changes; refusing to overwrite them" >&2
    exit 1
fi
if [[ ! -f .env.production ]]; then
    echo "Missing .env.production on the VPS" >&2
    exit 1
fi

GIT_TERMINAL_PROMPT=0 git fetch --no-tags origin "$deploy_branch"
if [[ $(git rev-parse FETCH_HEAD) != "$expected_sha" ]]; then
    echo "A newer commit is now on the deploy branch; skipping this run" >&2
    exit 1
fi
git merge --ff-only FETCH_HEAD
if [[ $(git rev-parse HEAD) != "$expected_sha" ]]; then
    echo "VPS checkout did not reach the tested commit" >&2
    exit 1
fi

compose=(docker compose --env-file .env.production -f compose.production.yml -f compose.nginx.yml)
"${compose[@]}" config --quiet
"${compose[@]}" up -d --build

for attempt in {1..30}; do
    if curl -fsS --max-time 5 -H "Host: $app_domain" \
        http://127.0.0.1:18080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        "${compose[@]}" ps
        echo "Deployed $expected_sha successfully"
        exit 0
    fi
    sleep 2
done

"${compose[@]}" logs --tail=100 backend caddy >&2
echo "Deployment health check failed" >&2
exit 1
