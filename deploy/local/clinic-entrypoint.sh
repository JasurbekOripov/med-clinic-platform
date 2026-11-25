#!/bin/sh
# Local trial only: trust the license key that the local (development) Vendor Platform generated.
set -e
KEY=/vendor-keys/vendor-license.pem.pub
until [ -s "$KEY" ]; do
  echo "waiting for the vendor license key..."
  sleep 2
done
CLINIC_DEV_KEYRING="vk-dev-1:$(grep -v -- '-----' "$KEY" | tr -d '\n')"
export CLINIC_DEV_KEYRING
exec java -jar /app/app.jar
