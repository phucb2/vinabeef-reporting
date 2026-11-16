#!/usr/bin/env bash
# Mints an HS256 JWT for the "local" profile. Usage: scripts/dev-token.sh [ROLE ...]   (default: MANAGER)
set -euo pipefail
SECRET="${LOCAL_JWT_SECRET:-local-dev-secret-change-me-0123456789abcdef}"
ROLES=("${@:-MANAGER}")
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
roles_json=$(printf '"%s",' "${ROLES[@]}"); roles_json="[${roles_json%,}]"
now=$(date +%s)
header=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"dev-user","roles":%s,"iat":%d,"exp":%d}' "$roles_json" "$now" $((now + 8*3600)) | b64url)
sig=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)
echo "$header.$payload.$sig"
