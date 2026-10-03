#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_dir="/d/Project Cherri/Compose Deploy App"
if [[ "${1:-}" == '--help' ]]; then
  echo 'Usage: bash scripts/Deploy-TikTok-Connector.sh [--prepare]'
  echo '--prepare copies/builds the connector; otherwise it also starts the container.'
  exit 0
fi
[[ $# == 0 || ( $# == 1 && "$1" == '--prepare' ) ]] || { echo 'Unknown argument.' >&2; exit 2; }
cd -- "$deploy_dir"
base='docker-compose.yml'
[[ ! -f compose.yml ]] || base='compose.yml'
[[ ! -f compose.yaml ]] || base='compose.yaml'
[[ -f "$base" ]] || { echo 'Backend Compose is missing.' >&2; exit 1; }
mkdir -p tiktok-connector/src
cp -- "$project_root"/tiktok-connector/{Dockerfile,package.json,package-lock.json,.dockerignore} tiktok-connector/
cp -- "$project_root"/tiktok-connector/src/*.mjs tiktok-connector/src/
cp -- "$project_root/tiktok-connector/compose.yml" compose.tiktok.yml
[[ -f .env.connector ]] || cp -- "$project_root/tiktok-connector/.env.example" .env.connector
docker compose -f "$base" -f compose.tiktok.yml --profile tiktok build tiktok-connector
if [[ "${1:-}" == '--prepare' ]]; then
  echo 'Prepared. Fill TIKTOK_USERNAME, CHERRI_LIVE_SESSION_ID, CHERRI_USERNAME and CHERRI_PASSWORD in .env.connector.'
  exit 0
fi
docker compose -f "$base" -f compose.tiktok.yml --profile tiktok run --rm --no-deps tiktok-connector node -e '
  if (!/^[\w.]{1,100}$/.test((process.env.TIKTOK_USERNAME || "").replace(/^@/, "")) || !/^[1-9]\d*$/.test(process.env.CHERRI_LIVE_SESSION_ID || "") || !process.env.CHERRI_PASSWORD) {
    console.error("Fill username, Cherri live session ID and Cherri password in .env.connector first."); process.exit(1);
  }'
docker compose -f "$base" -f compose.tiktok.yml --profile tiktok up -d --no-deps --force-recreate tiktok-connector
echo 'Connector started. Inspect: docker logs --tail 100 cherridiary_tiktok_connector'
