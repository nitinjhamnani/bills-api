#!/usr/bin/env bash
# Phase 5 deployment scaffolding - deploys api-app to Cloud Run backed by Cloud SQL Postgres.
#
# This script is NOT run as part of any automated process here - it requires a real GCP project,
# billing enabled, and credentials this environment does not have. Review every value below
# before running it yourself: `gcloud auth login` first, then `./deploy.sh`.
#
# Prerequisites (one-time, per project):
#   gcloud services enable run.googleapis.com sqladmin.googleapis.com artifactregistry.googleapis.com
#   gcloud sql instances create b2bfintech-pg --database-version=POSTGRES_17 \
#       --tier=db-f1-micro --region="$REGION"
#   gcloud sql databases create b2bfintech --instance=b2bfintech-pg
#   Store a real JWT secret and DB password in Secret Manager rather than inline env vars below.
#   The Cloud SQL socket-factory JDBC URL below requires adding
#   `com.google.cloud.sql:postgres-socket-factory` to api-app's pom.xml first - it isn't there
#   today since local/dev connects to Postgres.app over plain TCP.

set -euo pipefail

PROJECT_ID="${PROJECT_ID:?Set PROJECT_ID to your GCP project id}"
REGION="${REGION:-asia-south1}"
SERVICE_NAME="${SERVICE_NAME:-b2bfintech-api}"
CLOUD_SQL_INSTANCE="${CLOUD_SQL_INSTANCE:?Set to projectId:region:instanceName}"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/b2bfintech/${SERVICE_NAME}:$(git rev-parse --short HEAD)"

gcloud builds submit --project "$PROJECT_ID" --tag "$IMAGE" ..

gcloud run deploy "$SERVICE_NAME" \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --image "$IMAGE" \
  --add-cloudsql-instances "$CLOUD_SQL_INSTANCE" \
  --set-env-vars "SPRING_DATASOURCE_URL=jdbc:postgresql:///b2bfintech?cloudSqlInstance=${CLOUD_SQL_INSTANCE}&socketFactory=com.google.cloud.sql.postgres.SocketFactory,BILLING_PROVIDER=kbill,KBILL_BASE_URL=https://kbillpay.com/api/kbill/v3/customer/bbps" \
  --set-secrets "DB_USERNAME=b2bfintech-db-username:latest,DB_PASSWORD=b2bfintech-db-password:latest,JWT_SECRET=b2bfintech-jwt-secret:latest,KBILL_API_TOKEN=b2bfintech-kbill-api-token:latest" \
  --allow-unauthenticated \
  --port 8081

echo "Deployed."
echo "KBill only accepts calls from a registered public IP. Register this service's egress IP before BILLING_PROVIDER=kbill will succeed."
echo "Store the KBill token in Secret Manager as b2bfintech-kbill-api-token. Do not put it in this script."
