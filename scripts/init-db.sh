#!/usr/bin/env bash
# 새 DB를 만든다 — 테이블 생성 + 기본 데이터 (2026-10-10)
# CI(.github/workflows/ci.yml)와 README "새 DB라면"이 같은 순서를 쓴다. 순서를 바꾸면 둘 다 여기 하나만 고치면 된다.
#
# 사용: scripts/init-db.sh <DB이름> [mysql 접속 옵션...]
#   예) scripts/init-db.sh spec_odyssey -h 127.0.0.1 -P 3306 -u root -p
# DB는 미리 만들어 둔다(CREATE DATABASE spec_odyssey CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci).
# 01~03에 이미 반영된 ALTER(21·26·27·28·30~34·36~39 등)는 새 DB에서 돌리지 않는다.
set -euo pipefail

if [ $# -lt 1 ]; then
  sed -n '2,8p' "$0"
  exit 1
fi
db="$1"
shift
dir="$(cd "$(dirname "$0")/../sql" && pwd)"

files=(
  01_schema 02_seed 03_schema_extended 04_seed_extended 04_seed_skills 05_seed_survey
  09_schema_skill_alias 10_seed_skill_alias 11_seed_skill_alias_english 19_seed_skill_alias_more
  23_seed_certification_common 24_schema_notification 29_schema_admin_audit_log 35_seed_skill_prerequisite
)
for f in "${files[@]}"; do
  echo "== $f"
  mysql --default-character-set=utf8mb4 "$@" "$db" < "$dir/$f.sql"
done
echo "완료: ${#files[@]}개 파일"
