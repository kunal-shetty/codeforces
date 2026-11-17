#!/usr/bin/env bash
# Downloads the Codeforces problem pages listed in problems.txt into ./pages.
# Pages are fetched politely (one at a time, with a pause) and are not committed.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p pages
while read -r id; do
  [ -z "$id" ] && continue
  out="pages/$id.html"
  [ -s "$out" ] && continue
  contest="${id%_*}"
  index="${id#*_}"
  if curl -sfL -A "Mozilla/5.0 (Upsolve template-check)" "https://codeforces.com/contest/$contest/problem/$index" -o "$out"; then
    echo "got $id"
  else
    echo "failed $id" >&2
    rm -f "$out"
  fi
  sleep 0.5
done < problems.txt
