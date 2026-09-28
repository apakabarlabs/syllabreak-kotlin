#!/usr/bin/env bash
set -euo pipefail

refs="$(git ls-remote --tags origin "refs/tags/$1" "refs/tags/$1^{}")"
peeled="$(awk -v ref="refs/tags/$1^{}" '$2 == ref { print $1 }' <<< "$refs")"
direct="$(awk -v ref="refs/tags/$1" '$2 == ref { print $1 }' <<< "$refs")"
echo "${peeled:-$direct}"
