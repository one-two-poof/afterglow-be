#!/usr/bin/env bash
# data/out/buildings.geojson(서울 전체) -> data/out/buildings.pmtiles 변환.
#
# tippecanoe는 Windows 네이티브 빌드가 안 돼 WSL(Ubuntu)에 소스 빌드해 둔 것을
# 사용한다. Windows에서 실행할 때는:
#   wsl -d Ubuntu bash batch/src/make_tiles.sh
# WSL 안에서 직접 실행할 때는:
#   bash batch/src/make_tiles.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INPUT="$REPO_ROOT/data/out/buildings.geojson"
OUTPUT="$REPO_ROOT/data/out/buildings.pmtiles"
LAYER_NAME="buildings"

if ! command -v tippecanoe >/dev/null 2>&1; then
    echo "tippecanoe가 설치되어 있지 않습니다 (WSL Ubuntu에 빌드해 두었는지 확인하세요)." >&2
    exit 1
fi

if [ ! -f "$INPUT" ]; then
    echo "입력 파일이 없습니다: $INPUT" >&2
    exit 1
fi

echo "입력: $INPUT"
echo "출력: $OUTPUT"

tippecanoe \
    -o "$OUTPUT" \
    -zg \
    --drop-densest-as-needed \
    --force \
    -l "$LAYER_NAME" \
    -y BLD_ID -y ADDR -y USE_NAME -y HEIGHT \
    "$INPUT"

echo "완료: $OUTPUT"
