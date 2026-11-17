#!/usr/bin/env bash
# Compiles data/Template.java on its own (it has no Android dependencies) and scores it on ./pages.
#   ./run.sh              score every page
#   ./run.sh 1741_D       score, and print the generated code for 1741D
#   ./run.sh --compile    score, then compile every generated template with g++
set -euo pipefail
cd "$(dirname "$0")"
[ -d pages ] || { echo "No pages yet. Run ./fetch.sh first."; exit 1; }
rm -rf out && mkdir -p out
javac -nowarn -encoding UTF-8 -d out ../../app/src/main/java/com/kun/cfdaily/data/Template.java Run.java

if [ "${1:-}" = "--compile" ]; then
  java -cp out Run pages --dump out/cpp
  fails=0
  for f in out/cpp/*.cpp; do
    g++ -std=c++17 -fsyntax-only "$f" 2>/dev/null || { echo "does not compile: $f"; fails=$((fails + 1)); }
  done
  echo "compile failures: $fails"
  [ "$fails" -eq 0 ]
else
  java -cp out Run pages "${1:-}"
fi
