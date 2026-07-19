#!/usr/bin/env bash
# Generate a .env for the Docker deployment: a fresh RSA key pair for RS256 JWTs
# and strong random DB/Redis passwords. Idempotent guard: refuses to clobber an
# existing .env.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=".env"

if [ -f "$ENV_FILE" ]; then
  echo "Refusing to overwrite existing $ENV_FILE. Delete it first for fresh secrets." >&2
  exit 1
fi

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# RSA 2048 key pair: PKCS#8 private, X.509 (SubjectPublicKeyInfo) public — the
# formats JwtKeyConfig parses. Emit as a single-line base64 body (armor stripped).
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/private.pem" 2>/dev/null
openssl rsa -in "$tmp/private.pem" -pubout -out "$tmp/public.pem" 2>/dev/null

priv="$(grep -v -- '-----' "$tmp/private.pem" | tr -d '\n')"
pub="$(grep -v -- '-----' "$tmp/public.pem" | tr -d '\n')"

# URL/YAML-safe random passwords (strip +/= so no quoting surprises anywhere).
db_pw="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"
redis_pw="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"

umask 077
cat > "$ENV_FILE" <<EOF
SPRING_PROFILES_ACTIVE=prod
WEB_PORT=80
DB_PASSWORD=$db_pw
REDIS_PASSWORD=$redis_pw
JWT_PRIVATE_KEY=$priv
JWT_PUBLIC_KEY=$pub
FRONTEND_ORIGIN=http://localhost
EOF

echo "Wrote $ENV_FILE (mode 600) with a fresh JWT key pair and strong DB/Redis passwords."
echo "Next: docker compose up -d --build"
