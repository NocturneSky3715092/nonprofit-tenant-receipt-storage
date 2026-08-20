#!/bin/sh
set -eu

OUT="${TMPDIR:-/tmp}/nonprofit-tenant-storage-classes"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java src/test/java -name '*.java')

case "${1:-test}" in
  test) exec java -cp "$OUT" org.example.nonprofit.ReceiptStorageServiceTest ;;
  serve) exec java -cp "$OUT" org.example.nonprofit.ReceiptUploadServer ;;
  *) echo "usage: $0 [test|serve]" >&2; exit 2 ;;
esac

