#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${SLEEP_API_URL:-http://localhost:8080}"
USER_ID="${SLEEP_USER_ID:-1}"

usage() {
  cat <<EOF
Sleep Logger CLI

Usage: $0 <command> [options]

Commands:
  log <bedTime> <wakeTime> <mood>   Log today's sleep (times as ISO 8601)
  today                             Get today's sleep log
  stats                             Get 30-day sleep statistics

Options:
  -u, --user-id <id>   Set user ID (default: \$SLEEP_USER_ID or 1)
  -b, --base-url <url> Set API base URL (default: \$SLEEP_API_URL or http://localhost:8080)

Examples:
  $0 log 2024-01-14T22:30:00Z 2024-01-15T06:45:00Z GOOD
  $0 log 2024-01-14T23:00:00-05:00 2024-01-15T07:15:00-05:00 OK
  $0 today
  $0 stats
  $0 -u 42 today

Bed/wake times must be ISO 8601 date-times with offset (e.g. 2024-01-14T22:30:00Z).
Moods: BAD, OK, GOOD
EOF
  exit 1
}

# Parse global options
while [[ $# -gt 0 ]]; do
  case "$1" in
    -u|--user-id) USER_ID="$2"; shift 2 ;;
    -b|--base-url) BASE_URL="$2"; shift 2 ;;
    -*) echo "Unknown option: $1" >&2; usage ;;
    *) break ;;
  esac
done

[[ $# -eq 0 ]] && usage

COMMAND="$1"; shift

pretty_print() {
  if command -v jq &>/dev/null; then
    jq .
  else
    cat
  fi
}

handle_response() {
  local http_code body
  body=$(cat)
  http_code=$(tail -1 <<< "$body")
  body=$(sed '$d' <<< "$body")

  if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
    echo "$body" | pretty_print
  else
    echo "Error (HTTP $http_code):" >&2
    echo "$body" | pretty_print >&2
    return 1
  fi
}

case "$COMMAND" in
  log)
    [[ $# -ne 3 ]] && { echo "Usage: $0 log <bedTime> <wakeTime> <mood>" >&2; exit 1; }
    BED_TIME="$1"
    WAKE_TIME="$2"
    MOOD="$3"

    curl -s -w '\n%{http_code}' \
      -X POST "${BASE_URL}/api/v1/sleep-log" \
      -H "Content-Type: application/json" \
      -H "X-User-Id: ${USER_ID}" \
      -d "{\"bedTime\":\"${BED_TIME}\",\"wakeTime\":\"${WAKE_TIME}\",\"mood\":\"${MOOD}\"}" \
      | handle_response
    ;;

  today)
    curl -s -w '\n%{http_code}' \
      -X GET "${BASE_URL}/api/v1/sleep-log" \
      -H "X-User-Id: ${USER_ID}" \
      | handle_response
    ;;

  stats)
    curl -s -w '\n%{http_code}' \
      -X GET "${BASE_URL}/api/v1/sleep-stats" \
      -H "X-User-Id: ${USER_ID}" \
      | handle_response
    ;;

  *)
    echo "Unknown command: $COMMAND" >&2
    usage
    ;;
esac
