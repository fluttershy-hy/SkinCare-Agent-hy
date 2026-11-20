#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn_cmd="${MVN:-mvn}"
classpath=$(mktemp)
trap 'rm -f "$classpath"' EXIT
"$mvn_cmd" -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile="$classpath"
java -Xmx1g -cp "target/classes:$(cat "$classpath")" com.skincarecopilot.knowledge.BookVectorIndex "${SKINCARE_BOOK_TEXT_DIR:-data/book-text}" "${SKINCARE_BOOK_VECTOR_DIR:-data/book-vectors}"
