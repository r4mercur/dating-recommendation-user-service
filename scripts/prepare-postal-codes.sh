#!/usr/bin/env bash
# Downloads the GeoNames postal codes of a country and writes the reduced file that
# PostalCodeImporter loads on application start (src/main/resources/geo is gitignored).
#
# Usage: scripts/prepare-postal-codes.sh [COUNTRY]   (default: DE)
# Data: GeoNames (https://www.geonames.org), licensed under CC BY 4.0.
set -euo pipefail

COUNTRY="$(echo "${1:-DE}" | tr '[:lower:]' '[:upper:]')"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT/src/main/resources/geo"
TARGET="$TARGET_DIR/postal-codes-$COUNTRY.tsv"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

curl -sSfLo "$WORK_DIR/$COUNTRY.zip" "https://download.geonames.org/export/zip/$COUNTRY.zip"
unzip -q -o "$WORK_DIR/$COUNTRY.zip" "$COUNTRY.txt" -d "$WORK_DIR"

mkdir -p "$TARGET_DIR"
{
  printf '# GeoNames postal codes for %s (https://www.geonames.org), licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).\n' "$COUNTRY"
  printf '# Reduced to: postal_code, place_name, state, latitude, longitude. Bulk-receiver entries (companies) removed.\n'
  # columns: 1 country, 2 postal code, 3 place, 4 state, ..., 10 latitude, 11 longitude
  awk -F'\t' -v country="$COUNTRY" 'BEGIN { OFS = "\t" }
    $1 == country && $3 !~ /(GmbH|[[:space:]]AG$|[[:space:]]AG[[:space:]]|[[:space:]]KG$|e\. ?V\.|Postfach)/ {
      key = $2 FS $3
      if (!(key in seen)) { seen[key] = 1; print $2, $3, $4, $10, $11 }
    }' "$WORK_DIR/$COUNTRY.txt" | LC_ALL=C sort -t$'\t' -k1,1 -k2,2
} > "$TARGET"

echo "Wrote $(grep -vc '^#' "$TARGET") postal codes to $TARGET"
