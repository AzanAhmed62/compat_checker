#!/usr/bin/env bash
# Rebuild a disposable PostgreSQL database and run every SQL/RLS test file.
#
# This is the plain-PostgreSQL Linux/macOS path for validating the Supabase
# migrations. It applies the plain-PostgreSQL Supabase shim, ordered migrations,
# deterministic seed, and SQL test suite. Never point it at a remote database.
set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration_dir="$repo_root/supabase/migrations"
test_dir="$repo_root/supabase/tests"

database="${DATABASE:-compat_test}"
port="${PGPORT:-55432}"
user="${PGUSER:-postgres}"
host="${PGHOST:-localhost}"
keep=0

usage() {
  cat <<'EOF'
Usage: scripts/db_test.sh [--keep] [--database NAME] [--port PORT] [--user USER] [--host HOST]

Environment variables: PGPORT, PGUSER, PGHOST, PGPASSWORD, DATABASE.
The database name must match compat_* so this can never target a remote project.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep|-k) keep=1; shift ;;
    --database) database="${2:?missing database name}"; shift 2 ;;
    --port) port="${2:?missing port}"; shift 2 ;;
    --user) user="${2:?missing user}"; shift 2 ;;
    --host) host="${2:?missing host}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ ! "$database" =~ ^compat_[a-z0-9_]+$ ]]; then
  echo "Refusing unsafe database name '$database'. Use a compat_* disposable database." >&2
  exit 2
fi

for tool in psql dropdb createdb; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool was not found. Install PostgreSQL client tools." >&2
    exit 127
  fi
done

tmp_dir="${TMPDIR:-/tmp}"
mkdir -p "$tmp_dir"
log="$(mktemp "$tmp_dir/compat-db-test-XXXXXX")"
common=(-X -q -v ON_ERROR_STOP=1 -h "$host" -p "$port" -U "$user")
completed=0
created=0

cleanup() {
  [[ "$created" -eq 1 ]] || return 0
  if [[ "$completed" -eq 1 && "$keep" -eq 0 ]]; then
    dropdb -h "$host" -p "$port" -U "$user" --if-exists --force "$database" >/dev/null 2>&1 || true
  else
    echo "Database '$database' kept for inspection. Full transcript: $log"
  fi
}
trap cleanup EXIT

run_file() {
  local path="$1"
  local name
  name="$(basename "$path")"
  if ! psql "${common[@]}" -d "$database" -f "$path" >>"$log" 2>&1; then
    echo
    echo "--- output ($name) ---" >&2
    tail -n 30 "$log" >&2
    echo "SQL failed: ${path#"$repo_root"/}" >&2
    exit 1
  fi
}

echo "Rebuilding disposable database '$database' on $host:$port ..."
dropdb -h "$host" -p "$port" -U "$user" --if-exists --force "$database" >/dev/null 2>&1 || true
createdb -h "$host" -p "$port" -U "$user" "$database"
created=1

run_file "$test_dir/00_shim.sql"

migrations=()
for migration_path in "$migration_dir"/*.sql; do
  [[ -e "$migration_path" ]] || continue
  migrations+=("$(basename "$migration_path")")
done
if [[ "${#migrations[@]}" -eq 0 ]]; then
  echo "No migrations found." >&2
  exit 1
fi
for migration in "${migrations[@]}"; do
  echo "  apply $migration"
  run_file "$migration_dir/$migration"
done

echo "  apply seed.sql"
run_file "$repo_root/supabase/seed.sql"

run_file "$test_dir/01_setup.sql"

tests=()
for test_path in "$test_dir"/[0-9][0-9]_*.sql; do
  [[ -e "$test_path" ]] || continue
  test_name="$(basename "$test_path")"
  case "$test_name" in
    00_shim.sql|01_setup.sql) continue ;;
  esac
  tests+=("$test_name")
done
if [[ "${#tests[@]}" -eq 0 ]]; then
  echo "No tests found." >&2
  exit 1
fi

failed=0
for test in "${tests[@]}"; do
  echo "  test  $test"
  if psql "${common[@]}" -d "$database" -f "$test_dir/$test" >>"$log" 2>&1; then
    echo "        PASS"
  else
    failed=$((failed + 1))
    echo "        FAIL"
    tail -n 30 "$log" | sed 's/^/        /'
  fi
done

if [[ "$failed" -gt 0 ]]; then
  echo
  echo "$failed test file(s) failed. Full transcript: $log" >&2
  exit 1
fi

echo
echo "All ${#tests[@]} SQL test files passed."
completed=1
