#!/usr/bin/env bash
set -u
root="${1:-data/book-sources}"
out="${2:-data/book-text}"
export OMP_THREAD_LIMIT=1
export TESSDATA_PREFIX="${TESSDATA_PREFIX:-/workspace/.tools/tessdata}"
mkdir -p "$out"
find "$root" -type f -name '*.pdf' -print0 | sort -z | while IFS= read -r -d '' file; do
  relative="${file#"$root"/}"
  directory="$out/${relative%.pdf}"
  mkdir -p "$directory"
  pages=$(pdfinfo "$file" 2>/dev/null | awk '/^Pages:/ {print $2}')
  for ((page=1; page<=pages; page++)); do
    target=$(printf '%s/%04d.txt' "$directory" "$page")
    [[ -s "$target" ]] && continue
    tmp=$(mktemp -d)
    if pdftoppm -f "$page" -l "$page" -scale-to 1600 -gray -png -singlefile "$file" "$tmp/page" 2>/dev/null && tesseract "$tmp/page.png" "$tmp/text" -l chi_sim --psm 6 2>/dev/null && [[ -s "$tmp/text.txt" ]]; then
      mv "$tmp/text.txt" "$target"
    else
      printf 'OCR failed: %s page %s\n' "$relative" "$page" >&2
    fi
    rm -rf "$tmp"
  done
  printf 'Processed %s (%s pages)\n' "$relative" "$pages"
done
