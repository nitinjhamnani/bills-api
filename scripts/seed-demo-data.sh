#!/usr/bin/env bash
# Seeds a rich demo dataset on top of the Phase 0 baseline (platform admin 9999999999 +
# Acme Corp / Jane Doe 9876543210, created during earlier manual testing). Idempotent-ish: safe
# to re-run against a fresh database (it recreates Acme Corp if missing), but re-running against
# a database that already has this data will fail on the duplicate-phone-number checks - that's
# intentional, this is a one-shot demo seeder, not a migration.
#
# Usage: BASE=http://localhost:8081 ./scripts/seed-demo-data.sh
set -euo pipefail

BASE="${BASE:-http://localhost:8081}"
PASSWORD="${AUTH_DEMO_PASSWORD:-Password@123}"

jqget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)"; }

token() {
  curl -s -X POST "$BASE/api/v1/auth/password/login" -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"$1\",\"password\":\"$PASSWORD\"}" | jqget "['accessToken']"
}

echo "== Platform admin: ensure Acme Corp exists =="
PLATFORM_TOKEN=$(token 9999999999)
if ! curl -s "$BASE/api/v1/platform/organisations" -H "Authorization: Bearer $PLATFORM_TOKEN" | grep -q acmecorp; then
  curl -s -X POST "$BASE/api/v1/platform/organisations" -H "Authorization: Bearer $PLATFORM_TOKEN" -H 'Content-Type: application/json' \
    -d '{"organisationCode":"acmecorp","organisationName":"Acme Corp Pvt Ltd","adminPhoneNumber":"9876543210","adminName":"Jane Doe"}' > /dev/null
  echo "  created acmecorp"
else
  echo "  acmecorp already exists"
fi

echo "== Platform admin: create Beta Traders (second tenant, for platform-level variety) =="
curl -s -X POST "$BASE/api/v1/platform/organisations" -H "Authorization: Bearer $PLATFORM_TOKEN" -H 'Content-Type: application/json' \
  -d '{"organisationCode":"betatraders","organisationName":"Beta Traders LLP","adminPhoneNumber":"9888800001","adminName":"Beta Admin"}' > /dev/null || echo "  (already exists, continuing)"

ORGANISATION_TOKEN=$(token 9876543210)

echo "== Acme: enable more bill services =="
curl -s -X PUT "$BASE/api/v1/organisation/bill-services/WATER" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PUT "$BASE/api/v1/organisation/bill-services/GAS" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PUT "$BASE/api/v1/organisation/bill-services/DTH" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PUT "$BASE/api/v1/organisation/bill-services/MOBILE_POSTPAID" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null

echo "== Acme: create Maker/Checker/Viewer personas =="
curl -s -X POST "$BASE/api/v1/organisation/users" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"9876500001","fullName":"Manoj Maker","role":"MAKER"}' > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/users" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"9876500002","fullName":"Chitra Checker","role":"CHECKER"}' > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/users" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"9876500003","fullName":"Vikram Viewer","role":"VIEWER"}' > /dev/null

echo "== Acme: create a second client (QuickPay) =="
curl -s -X POST "$BASE/api/v1/organisation/clients" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' \
  -d '{"clientName":"QuickPay Solutions","contactPhone":"9222222222","adminPhoneNumber":"9222222222","adminName":"Quick Pay Admin"}' > /dev/null

echo "== Acme: add an Client Operator persona to FastPay =="
FASTPAY_ID=$(curl -s "$BASE/api/v1/organisation/clients" -H "Authorization: Bearer $ORGANISATION_TOKEN" | python3 -c "import json,sys; [print(a['id']) for a in json.load(sys.stdin) if a['clientName']=='FastPay Services']")
QUICKPAY_ID=$(curl -s "$BASE/api/v1/organisation/clients" -H "Authorization: Bearer $ORGANISATION_TOKEN" | python3 -c "import json,sys; [print(a['id']) for a in json.load(sys.stdin) if a['clientName']=='QuickPay Solutions']")
echo "  FASTPAY_ID=$FASTPAY_ID QUICKPAY_ID=$QUICKPAY_ID"
curl -s -X POST "$BASE/api/v1/organisation/clients/$FASTPAY_ID/users" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"9111100002","fullName":"Ops Operator","role":"CLIENT_OPERATOR"}' > /dev/null

echo "== Acme: per-client bill-service activation =="
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$FASTPAY_ID/bill-services/WATER/status" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$FASTPAY_ID/bill-services/GAS/status" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$FASTPAY_ID/bill-services/DTH/status" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$FASTPAY_ID/bill-services/MOBILE_POSTPAID/status" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$QUICKPAY_ID/bill-services/ELECTRICITY/status" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$QUICKPAY_ID/bill-services/WATER/status" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null

echo "== Top up wallets (request, then organisation accepts) =="
FASTPAY_TOKEN=$(token 9111111111)
QUICKPAY_TOKEN=$(token 9222222222)
FASTPAY_TOPUP=$(curl -s -X POST "$BASE/api/v1/clients/$FASTPAY_ID/wallet/topup" -H "Authorization: Bearer $FASTPAY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount":10000,"paymentMode":"NEFT","paymentReference":"NEFT-SEED-FP-1","idempotencyKey":"seed-topup-fastpay-1"}' | jqget "['id']")
QUICKPAY_TOPUP=$(curl -s -X POST "$BASE/api/v1/clients/$QUICKPAY_ID/wallet/topup" -H "Authorization: Bearer $QUICKPAY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount":8000,"paymentMode":"UPI","paymentReference":"UPI-SEED-QP-1","idempotencyKey":"seed-topup-quickpay-1"}' | jqget "['id']")
curl -s -X POST "$BASE/api/v1/organisation/wallet-topups/$FASTPAY_TOPUP/review" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"approve":true}' > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/wallet-topups/$QUICKPAY_TOPUP/review" -H "Authorization: Bearer $ORGANISATION_TOKEN" -H 'Content-Type: application/json' -d '{"approve":true}' > /dev/null

echo "== Create managed bills (each is fetched from the dummy biller immediately) =="
create_managed_bill() {
  local token="$1" client="$2" service="$3" biller="$4" acct="$5" mobile="$6"
  curl -s -X POST "$BASE/api/v1/clients/$client/managed-bills" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"billServiceCode\":\"$service\",\"billerId\":\"$biller\",\"customerBillAccountNumber\":\"$acct\",\"customerMobileNumber\":\"$mobile\"}" \
    | jqget "['id']"
}
WATER_BILL=$(create_managed_bill "$FASTPAY_TOKEN" "$FASTPAY_ID" WATER WATER-DJB FP-WTR-01 9111100001)
GAS_BILL=$(create_managed_bill "$FASTPAY_TOKEN" "$FASTPAY_ID" GAS GAS-MGL FP-GAS-01 9111100001)
DTH_BILL=$(create_managed_bill "$FASTPAY_TOKEN" "$FASTPAY_ID" DTH DTH-TATAPLAY FP-DTH-01 9111100001)
MOBILE_BILL=$(create_managed_bill "$FASTPAY_TOKEN" "$FASTPAY_ID" MOBILE_POSTPAID MOB-AIRTEL FP-MOB-01 9111100001)
ELEC_BILL=$(create_managed_bill "$FASTPAY_TOKEN" "$FASTPAY_ID" ELECTRICITY ELEC-TATAPOWER-MUM FP-ELE-01 9111100001)
QP_ELEC_BILL=$(create_managed_bill "$QUICKPAY_TOKEN" "$QUICKPAY_ID" ELECTRICITY ELEC-BEST-MUM QP-ELE-01 9222200001)
create_managed_bill "$QUICKPAY_TOKEN" "$QUICKPAY_ID" WATER WATER-BWSSB QP-WTR-01 9222200001 > /dev/null

echo "  WATER_BILL=$WATER_BILL GAS_BILL=$GAS_BILL DTH_BILL=$DTH_BILL MOBILE_BILL=$MOBILE_BILL ELEC_BILL=$ELEC_BILL QP_ELEC_BILL=$QP_ELEC_BILL"

payment_id_for_account() {
  local token="$1" client="$2" acct="$3"
  curl -s "$BASE/api/v1/clients/$client/my-payments" -H "Authorization: Bearer $token" \
    | python3 -c "import json,sys; [print(p['id']) for p in json.load(sys.stdin) if p['customerBillAccountNumber']=='$acct']"
}

echo "== FastPay pays the WATER/GAS/ELECTRICITY bills from My Payments, leaves DTH/MOBILE unpaid on purpose =="
FP_WATER_PAYMENT=$(payment_id_for_account "$FASTPAY_TOKEN" "$FASTPAY_ID" FP-WTR-01)
FP_GAS_PAYMENT=$(payment_id_for_account "$FASTPAY_TOKEN" "$FASTPAY_ID" FP-GAS-01)
FP_ELEC_PAYMENT=$(payment_id_for_account "$FASTPAY_TOKEN" "$FASTPAY_ID" FP-ELE-01)
curl -s -X POST "$BASE/api/v1/clients/$FASTPAY_ID/my-payments/pay" -H "Authorization: Bearer $FASTPAY_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"billPaymentIds\":[\"$FP_WATER_PAYMENT\",\"$FP_GAS_PAYMENT\",\"$FP_ELEC_PAYMENT\"]}" > /dev/null

echo "== QuickPay pays its electricity bill too =="
QP_ELEC_PAYMENT=$(payment_id_for_account "$QUICKPAY_TOKEN" "$QUICKPAY_ID" QP-ELE-01)
curl -s -X POST "$BASE/api/v1/clients/$QUICKPAY_ID/my-payments/pay" -H "Authorization: Bearer $QUICKPAY_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"billPaymentIds\":[\"$QP_ELEC_PAYMENT\"]}" > /dev/null

MAKER_TOKEN=$(token 9876500001)
CHECKER_TOKEN=$(token 9876500002)

echo "== Organisation maker picks up WATER and ELECTRICITY, leaves GAS pending pick-up on purpose =="
curl -s -X POST "$BASE/api/v1/organisation/my-payments/$FP_WATER_PAYMENT/pick-up" -H "Authorization: Bearer $MAKER_TOKEN" > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/my-payments/$FP_ELEC_PAYMENT/pick-up" -H "Authorization: Bearer $MAKER_TOKEN" > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/my-payments/$QP_ELEC_PAYMENT/pick-up" -H "Authorization: Bearer $MAKER_TOKEN" > /dev/null

echo "== Checker marks the electricity bills paid, leaves WATER in-process on purpose =="
curl -s -X POST "$BASE/api/v1/organisation/my-payments/$FP_ELEC_PAYMENT/mark-paid" -H "Authorization: Bearer $CHECKER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"bbpsTransactionId":"BBPS-SEED-FP-ELEC","billType":"BBPS"}' > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/my-payments/$QP_ELEC_PAYMENT/mark-paid" -H "Authorization: Bearer $CHECKER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"bbpsTransactionId":"BBPS-SEED-QP-ELEC","billType":"BBPS"}' > /dev/null

echo "== FastPay: raise a dispute on the settled electricity bill payment (left open for a checker to resolve) =="
curl -s -X POST "$BASE/api/v1/bill-payments/$FP_ELEC_PAYMENT/disputes" -H "Authorization: Bearer $FASTPAY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"reason":"Customer says this was already paid via another channel"}' > /dev/null

echo "== QuickPay: submit a top-up request with a proof upload (left pending for review) =="
echo "Demo bank deposit receipt - Rs 2000 - seeded for QuickPay Solutions" > /tmp/quickpay-receipt.txt
DOC_ID=$(curl -s -X POST "$BASE/api/v1/documents" -H "Authorization: Bearer $QUICKPAY_TOKEN" \
  -F "ownerType=WALLET_TOPUP_PROOF" -F "file=@/tmp/quickpay-receipt.txt;type=text/plain" | jqget "['id']")
curl -s -X POST "$BASE/api/v1/clients/$QUICKPAY_ID/wallet/topup" -H "Authorization: Bearer $QUICKPAY_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"amount\":2000,\"paymentMode\":\"CASH\",\"paymentReference\":\"CASH-SEED-QP-2\",\"documentId\":\"$DOC_ID\",\"idempotencyKey\":\"seed-pending-topup-qp-1\"}" > /dev/null
rm -f /tmp/quickpay-receipt.txt

echo "== Beta Traders: minimal second-tenant activity =="
BETA_TOKEN=$(token 9888800001)
curl -s -X PUT "$BASE/api/v1/organisation/bill-services/ELECTRICITY" -H "Authorization: Bearer $BETA_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
curl -s -X POST "$BASE/api/v1/organisation/clients" -H "Authorization: Bearer $BETA_TOKEN" -H 'Content-Type: application/json' \
  -d '{"clientName":"Beta Client Services","contactPhone":"9888900001","adminPhoneNumber":"9888900001","adminName":"Beta Client Admin"}' > /dev/null
BETA_CLIENT_ID=$(curl -s "$BASE/api/v1/organisation/clients" -H "Authorization: Bearer $BETA_TOKEN" | jqget "[0]['id']")
curl -s -X PATCH "$BASE/api/v1/organisation/clients/$BETA_CLIENT_ID/bill-services/ELECTRICITY/status" -H "Authorization: Bearer $BETA_TOKEN" -H 'Content-Type: application/json' -d '{"enabled":true}' > /dev/null
BETA_CLIENT_TOKEN=$(token 9888900001)
BETA_TOPUP=$(curl -s -X POST "$BASE/api/v1/clients/$BETA_CLIENT_ID/wallet/topup" -H "Authorization: Bearer $BETA_CLIENT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"amount":3000,"paymentMode":"RTGS","paymentReference":"RTGS-SEED-BETA-1","idempotencyKey":"seed-topup-beta-1"}' | jqget "['id']")
curl -s -X POST "$BASE/api/v1/organisation/wallet-topups/$BETA_TOPUP/review" -H "Authorization: Bearer $BETA_TOKEN" -H 'Content-Type: application/json' -d '{"approve":true}' > /dev/null
curl -s -X POST "$BASE/api/v1/clients/$BETA_CLIENT_ID/managed-bills" -H "Authorization: Bearer $BETA_CLIENT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"billServiceCode":"ELECTRICITY","billerId":"ELEC-TATAPOWER-MUM","customerBillAccountNumber":"BETA-001","customerMobileNumber":"9888900001"}' > /dev/null

echo ""
echo "== Done. Demo personas (phone / password Password@123) =="
echo "  PLATFORM_ADMIN    9999999999  (platform)"
echo "  ORGANISATION_ADMIN     9876543210  Jane Doe (Acme Corp)"
echo "  MAKER             9876500001  Manoj Maker (Acme Corp)"
echo "  CHECKER           9876500002  Chitra Checker (Acme Corp)"
echo "  VIEWER            9876500003  Vikram Viewer (Acme Corp)"
echo "  CLIENT_ADMIN      9111111111  Ravi Kumar (FastPay)"
echo "  CLIENT_OPERATOR   9111100002  Ops Operator (FastPay)"
echo ""
echo "Pending actions left for a live demo:"
echo "  - GAS bill ($GAS_BILL) and MOBILE bill ($MOBILE_BILL) are still unpaid in FastPay's My Payments"
echo "  - QuickPay's pending top-up is waiting on a checker/organisation admin"
echo "  - The FastPay electricity dispute is waiting on a checker/organisation admin"
echo "  - The WATER bill payment is picked up but not yet marked paid, waiting on a checker"
