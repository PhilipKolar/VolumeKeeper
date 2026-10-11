#!/usr/bin/env bash
# Bump app/app.json "version" (semver) AND app/app.config.ts versionCode together.
#   ./scripts/bump-version.sh patch|minor|major
set -euo pipefail
KIND="${1:-patch}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_JSON="$REPO_ROOT/app/app.json"
APP_CONFIG="$REPO_ROOT/app/app.config.ts"

CUR=$(node -p "require('$APP_JSON').expo.version")
IFS=. read -r MA MI PA <<< "$CUR"
case "$KIND" in
  major) MA=$((MA+1)); MI=0; PA=0 ;;
  minor) MI=$((MI+1)); PA=0 ;;
  patch) PA=$((PA+1)) ;;
  *) echo "usage: $0 patch|minor|major" >&2; exit 1 ;;
esac
NEW="$MA.$MI.$PA"

node -e "
const fs=require('fs');const p='$APP_JSON';const j=JSON.parse(fs.readFileSync(p,'utf8'));
j.expo.version='$NEW';fs.writeFileSync(p,JSON.stringify(j,null,2)+'\n');"

CODE=$(grep -oE 'versionCode: [0-9]+' "$APP_CONFIG" | grep -oE '[0-9]+')
NEWCODE=$((CODE+1))
sed -i "s/versionCode: $CODE,/versionCode: $NEWCODE,/" "$APP_CONFIG"

echo "version $CUR → $NEW, versionCode $CODE → $NEWCODE"
echo "REMINDER: versionName/versionCode are baked into android/ at prebuild time; CI runs a clean"
echo "prebuild on every build, so just push and dispatch build-android.yml (never build locally)."
