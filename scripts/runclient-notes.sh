#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

require_cmd bash
require_cmd ./gradlew
if ! command -v rg >/dev/null 2>&1; then
  require_cmd grep
fi

only_pattern=""
from_project=""
to_project=""

usage() {
  cat <<USAGE
Usage: bash scripts/runclient-notes.sh [options]

Options:
  --only <glob>   Only include projects matching glob (e.g. 1.21.*-neoforge)
  --from <name>   Start from this project (inclusive)
  --to <name>     End at this project (inclusive)
  -h, --help      Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --only)
      only_pattern="${2:-}"
      shift 2
      ;;
    --from)
      from_project="${2:-}"
      shift 2
      ;;
    --to)
      to_project="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -n "$from_project" && -n "$to_project" && "$from_project" > "$to_project" ]]; then
  echo "Warning: --from is lexicographically after --to. Order is based on detected project sequence." >&2
fi

mkdir -p runchecks
run_id="$(date +%Y-%m-%d_%H-%M-%S)"
run_dir="runchecks/$run_id"
logs_dir="$run_dir/logs"
notes_file="$run_dir/notes.md"
summary_file="$run_dir/summary.tsv"

mkdir -p "$logs_dir"
printf '%s\n' "$run_id" > runchecks/latest.txt

cat > "$notes_file" <<NOTES
# runClient Developer Notes

- Run ID: $run_id
- Started: $(date '+%Y-%m-%d %H:%M:%S')
- Root: $ROOT_DIR

NOTES

printf 'project\tresult\truntime_s\tlog_file\n' > "$summary_file"

echo "Collecting projects from Gradle..."
projects_raw="$(./gradlew projects -q)"

if command -v rg >/dev/null 2>&1; then
  mapfile -t all_projects < <(printf '%s\n' "$projects_raw" | rg "Project ':1\.[0-9].*'" -o | sed "s/Project '//; s/'//" | sed 's/^://g' | sort -V)
else
  mapfile -t all_projects < <(printf '%s\n' "$projects_raw" | grep -o "Project ':1\.[0-9].*'" | sed "s/Project '//; s/'//" | sed 's/^://g' | sort -V)
fi

if [[ ${#all_projects[@]} -eq 0 ]]; then
  echo "No 1.* projects found." >&2
  exit 1
fi

projects=()
started_range=false
for proj in "${all_projects[@]}"; do
  if [[ -n "$only_pattern" && ! "$proj" == $only_pattern ]]; then
    continue
  fi

  if [[ -n "$from_project" && "$started_range" == false ]]; then
    if [[ "$proj" == "$from_project" ]]; then
      started_range=true
    else
      continue
    fi
  fi

  projects+=("$proj")

  if [[ -n "$to_project" && "$proj" == "$to_project" ]]; then
    break
  fi
done

if [[ ${#projects[@]} -eq 0 ]]; then
  echo "No projects matched provided filters." >&2
  exit 1
fi

total=${#projects[@]}
echo "Projects to run: $total"

cleanup_parser() {
  local parser_pid="$1"
  if [[ -n "$parser_pid" ]] && kill -0 "$parser_pid" 2>/dev/null; then
    kill "$parser_pid" 2>/dev/null || true
    wait "$parser_pid" 2>/dev/null || true
  fi
}

for idx in "${!projects[@]}"; do
  proj="${projects[$idx]}"
  log_file="$logs_dir/${proj}.log"
  start_ts="$(date '+%Y-%m-%d %H:%M:%S')"
  start_epoch="$(date +%s)"

  : > "$log_file"

  {
    echo "## $proj"
    echo
    echo "- Started: $start_ts"
    echo "- Developer Messages:"
  } >> "$notes_file"

  tail -n0 -F "$log_file" 2>/dev/null | awk -v notes="$notes_file" '
    {
      if (index($0, "<developer>") == 0) next

      msg = $0
      sub(/^.*<developer>[[:space:]]*/, "", msg)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", msg)
      if (msg == "") next

      ts = "unknown"
      if (match($0, /^\[[0-9]{2}:[0-9]{2}:[0-9]{2}\]/)) {
        ts = substr($0, RSTART + 1, RLENGTH - 2)
      }

      key = ts "\t" msg
      if (seen[key] == 1) next
      seen[key] = 1

      printf("  - `%s` %s\n", ts, msg) >> notes
      fflush(notes)
    }
  ' &
  parser_pid=$!

  echo "[$((idx + 1))/$total] START $proj"
  set +e
  ./gradlew --stacktrace ":${proj}:runClient" > "$log_file" 2>&1
  exit_code=$?
  set -e

  cleanup_parser "$parser_pid"

  end_ts="$(date '+%Y-%m-%d %H:%M:%S')"
  runtime_s="$(( $(date +%s) - start_epoch ))"

  if [[ $exit_code -eq 0 ]]; then
    result="EXIT_0"
  else
    result="EXIT_${exit_code}"
  fi

  {
    echo "- Ended: $end_ts"
    echo "- Result: $result"
    echo "- Runtime (s): $runtime_s"
    echo "- Manual Notes:"
    echo ""
    echo "  (wpisuj wiele linii; zakoncz wpisujac osobna linie: /done)"
  } >> "$notes_file"

  echo "[$((idx + 1))/$total] END $proj => $result (${runtime_s}s)"
  echo "Dodaj dodatkowe notatki dla $proj (zakoncz: /done):"

  manual_count=0
  while IFS= read -r line; do
    if [[ "$line" == "/done" ]]; then
      break
    fi
    printf '  %s\n' "$line" >> "$notes_file"
    manual_count=$((manual_count + 1))
  done

  if [[ $manual_count -eq 0 ]]; then
    echo "  (brak)" >> "$notes_file"
  fi

  echo >> "$notes_file"
  printf '%s\t%s\t%s\t%s\n' "$proj" "$result" "$runtime_s" "$log_file" >> "$summary_file"
done

{
  echo "## Run Summary"
  echo
  echo '```tsv'
  cat "$summary_file"
  echo '```'
  echo
} >> "$notes_file"

echo "Done."
echo "Notes: $notes_file"
echo "Logs:  $logs_dir"
echo "Summary: $summary_file"
