#!/usr/bin/env bash
set -euo pipefail

# Git Bash: bash scripts/Deploy-Backend.sh [deployment-directory]
if [[ "${1:-}" == "--help" ]]; then
  echo 'Usage: bash scripts/Deploy-Backend.sh ["/d/Project Cherri/Compose Deploy App"]'
  exit 0
fi
if (( $# > 1 )); then echo 'Expected at most one deployment directory.' >&2; exit 2; fi
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_dir="${1:-/d/Project Cherri/Compose Deploy App}"
if command -v cygpath >/dev/null 2>&1; then deploy_dir="$(cygpath -u "$deploy_dir")"; fi
command -v docker >/dev/null 2>&1 || { echo 'Docker is required. Start Docker Desktop.' >&2; exit 1; }
[[ -d "$deploy_dir" ]] || { echo "Deployment directory does not exist: $deploy_dir" >&2; exit 1; }
deploy_dir="$(cd -- "$deploy_dir" && pwd)"
[[ -f "$deploy_dir/compose.yml" || -f "$deploy_dir/compose.yaml" || -f "$deploy_dir/docker-compose.yml" || -f "$deploy_dir/docker-compose.yaml" ]] || {
  echo "No Compose file in $deploy_dir" >&2; exit 1;
}
[[ -f "$deploy_dir/.env" ]] || { echo 'Deployment .env is missing; configure the existing database and JWT first.' >&2; exit 1; }
cd -- "$deploy_dir"
docker compose config --quiet
services="$(docker compose config --services)"
[[ $'\n'"$services"$'\n' == *$'\nbackend\n'* ]] || { echo 'Compose must contain a backend service.' >&2; exit 1; }

cd -- "$project_root"
bash ./gradlew :backend:bootJar --console=plain
# bootJar creates one executable JAR; ignore the optional plain JAR.
shopt -s nullglob
jars=()
for jar in "$project_root"/backend/build/libs/*.jar; do
  [[ "$jar" == *-plain.jar ]] || jars+=("$jar")
done
(( ${#jars[@]} == 1 )) || { echo 'Expected exactly one executable JAR. Clean backend/build/libs and rebuild.' >&2; exit 1; }

staged_jar="$(mktemp "$deploy_dir/.backend.jar.XXXXXX")"
trap 'rm -f -- "$staged_jar"' EXIT
cp -- "${jars[0]}" "$staged_jar"
cd -- "$deploy_dir"
# Stop only the backend before replacing its bind-mounted package.
docker compose stop backend
mv -f -- "$staged_jar" "$deploy_dir/backend.jar"
docker compose up -d --force-recreate backend
docker compose ps backend
echo "Backend deployed: $deploy_dir/backend.jar"
echo 'View startup logs: docker compose logs --tail 100 backend'
echo 'If port 8080 is occupied by the old local backend, stop its terminal with Ctrl+C and run docker compose up -d backend again.'
