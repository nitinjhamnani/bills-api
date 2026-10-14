#!/usr/bin/env bash
# Hit one KBill UAT route with curl, which is known to pass Cloudflare.
# Usage: KBILL_API_TOKEN=... ./probe.sh /category
#        KBILL_API_TOKEN=... ./probe.sh '/operator-list?category_id=2'
set -euo pipefail
BASE="${KBILL_BASE_URL:-https://development.kbillpay.com/api/kbill/v3/customer/bbps}"
TOKEN="${KBILL_API_TOKEN:?Set KBILL_API_TOKEN}"
PATH_AND_QUERY="${1:?Usage: probe.sh /category}"
curl -sS -w "\n[HTTP %{http_code}]\n" "${BASE}${PATH_AND_QUERY}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "User-Agent: paytrix-b2bfintech/1.0"
