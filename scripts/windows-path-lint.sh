#!/usr/bin/env bash
# Fails if any tracked path cannot be checked out on Windows: forbidden characters
# (: < > | ? * " and backslash), reserved device names (CON PRN AUX NUL COM1-9 LPT1-9,
# with or without an extension), or a path component ending in a dot or a space.
set -euo pipefail

bad=0
while IFS= read -r -d '' path; do
  reason=""
  if [[ "$path" == *[:\<\>\|\?\*\"\\]* ]]; then
    reason="forbidden character"
  fi
  IFS='/' read -r -a parts <<< "$path"
  for part in "${parts[@]}"; do
    stem="${part%%.*}"
    upper="$(printf '%s' "$stem" | tr '[:lower:]' '[:upper:]')"
    case "$upper" in
      CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]) reason="reserved name '$part'" ;;
    esac
    if [[ "$part" == *. || "$part" == *" " ]]; then
      reason="component ends in dot or space '$part'"
    fi
  done
  if [[ -n "$reason" ]]; then
    printf 'windows-path-lint: %s: %s\n' "$reason" "$path" >&2
    bad=1
  fi
done < <(git ls-files -z)

if [[ "$bad" -ne 0 ]]; then
  exit 1
fi
echo ok
