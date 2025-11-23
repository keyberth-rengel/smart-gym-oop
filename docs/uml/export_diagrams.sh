#!/usr/bin/env bash
# Genera PNG y SVG para todos los .puml en este directorio.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${DIR}/export"
mkdir -p "$OUT"
FORMATs=(png svg)
if ! command -v plantuml >/dev/null 2>&1; then
  echo "ERROR: plantuml no encontrado. Instala con: brew install plantuml" >&2
  exit 1
fi
for f in "$DIR"/*.puml; do
  for fmt in "${FORMATs[@]}"; do
    plantuml -t"$fmt" "$f" -o "$OUT" >/dev/null
    echo "Generado $(basename "$f") -> $OUT/$(basename "${f%.puml}").$fmt"
  done
done
