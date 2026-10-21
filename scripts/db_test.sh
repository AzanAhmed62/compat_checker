#!/usr/bin/env bash
# Rebuilds a disposable PostgreSQL database and runs the SQL/RLS test suite.
# Docker-free fallback for environments without the Supabase CLI.
# Never point this script at a remote or production database.
set -euo pipefail

DATABASE="${DB_NAME:-compat_test}"
PORT="${PGPORT:-55432}"
PGUSER="${PGUSER:-postgres}"
PGHOST="${PGHOST:-localhost}"
KEEP="${KEEP_DB:-0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DIR="$ROOT/supabase/tests"

if [[ ! "$DATABASE" =~ ^compat_[a-z0-9_]+$ ]]; then
  echo "Refusing unsafe database name '$DATABASE'" >&2
  exit 2
fi

PSQL="${PSQL:-psql}"
command -v "$PSQL" >/dev/null 2>&1 || { echo "psql not found" >&2; exit 2; }

export PGPORT="$PORT" PGHOST="$PGHOST" PGUSER
LOG="$(mktemp -t compat-db-test.XXXXXX.log)"
SUCCEEDED=0

dropdb --if-exists --force "$DATABASE" >/dev/null
createdb "$DATABASE"

cleanup() {
  if [[ "$KEEP" == "1" || "$SUCCEEDED" != "1" ]]; then
    echo "Database '$DATABASE' kept for inspection (KEEP_DB=1 to silence)."
    echo "Transcript: $LOG"
  else
    dropdb --if-exists --force "$DATABASE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# Runs psql against the disposable database. Returns psql's exit code.
run_psql() {
  local file="$1"
  "$PSQL" -X -q -v ON_ERROR_STOP=1 -d "$DATABASE" -f "$file" >>"$LOG" 2>&1
}

require_file() {
  local file="$1"
  if ! run_psql "$file"; then
    echo "--- output ($(basename "$file")) ---" >&2
    tail -n 30 "$LOG" >&2
    echo "SQL failed: ${file#"$ROOT/"}" >&2
    exit 1
  fi
}

echo "Rebuilding disposable database '$DATABASE' on $PGHOST:$PORT ..."
require_file "$TEST_DIR/00_shim.sql"

for migration in "$ROOT"/supabase/migrations/*.sql; do
  echo "  apply $(basename "$migration")"
  require_file "$migration"
done

echo "  apply seed.sql"
require_file "$ROOT/supabase/seed.sql"
require_file "$TEST_DIR/01_setup.sql"

failed=0
tests=0
for test in "$TEST_DIR"/*.sql; do
  name="$(basename "$test")"
  case "$name" in
    00_shim.sql|01_setup.sql) continue ;;
  esac
  if [[ ! "$name" =~ ^[0-9]{2}_ ]]; then continue; fi
  tests=$((tests + 1))
  echo "  test  $name"
  if run_psql "$test"; then
    echo "        PASS"
  else
    failed=$((failed + 1))
    echo "        FAIL"
    tail -n 20 "$LOG" | sed 's/^/        /' >&2
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo "$failed of $tests test file(s) failed." >&2
  exit 1
fi

SUCCEEDED=1
echo "All $tests SQL test files passed."
