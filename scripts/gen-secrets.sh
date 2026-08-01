#!/usr/bin/env bash
# Generate a .env for the Docker deployment: a fresh RSA key pair for RS256 JWTs
# and strong random DB/Redis passwords. Idempotent guard: refuses to clobber an
# existing .env.
#
# Usage: ./scripts/gen-secrets.sh [<vm-ip-or-domain>] [<acme-email>]
#   With no arg it writes a placeholder sslip.io domain you must edit before
#   deploying. Pass your Azure VM's public IP (e.g. 20.1.2.3) to derive the
#   sslip.io hostname (20-1-2-3.sslip.io) automatically.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=".env"

# Resolve the public hostname. A bare IPv4 becomes a dashed sslip.io name; a
# domain (already contains a letter) is used as-is.
DOMAIN_ARG="${1:-}"
ACME_EMAIL="${2:-}"
if [ -z "$DOMAIN_ARG" ]; then
  APP_DOMAIN="20-1-2-3.sslip.io"   # placeholder — edit APP_DOMAIN + FRONTEND_ORIGIN before deploying
elif printf '%s' "$DOMAIN_ARG" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
  APP_DOMAIN="$(printf '%s' "$DOMAIN_ARG" | tr '.' '-').sslip.io"
else
  APP_DOMAIN="$DOMAIN_ARG"
fi

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
APP_DOMAIN=$APP_DOMAIN
ACME_EMAIL=$ACME_EMAIL
DB_PASSWORD=$db_pw
REDIS_PASSWORD=$redis_pw
JWT_PRIVATE_KEY=$priv
JWT_PUBLIC_KEY=$pub
FRONTEND_ORIGIN=https://$APP_DOMAIN
EOF

echo "Wrote $ENV_FILE (mode 600) with a fresh JWT key pair and strong DB/Redis passwords."
echo "App domain: $APP_DOMAIN  ->  https://$APP_DOMAIN"
if [ -z "$DOMAIN_ARG" ]; then
  echo "WARNING: APP_DOMAIN is a placeholder. Edit .env (APP_DOMAIN + FRONTEND_ORIGIN)"
  echo "         to your VM's <ip>.sslip.io before deploying, or re-run with your IP."
fi
echo "Next: open ports 80 + 443 in your Azure NSG, then: docker compose up -d --build"
