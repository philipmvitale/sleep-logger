#!/bin/bash
# SessionStart hook for Claude Code on the web.
#
# Prepares the environment so the Testcontainers-based integration tests can run:
#   1. Ensures the Docker daemon is running (web containers don't start it by default).
#   2. Disables the Testcontainers Ryuk reaper so it never needs to pull
#      testcontainers/ryuk (the container is ephemeral, so no reaping is needed).
#   3. Pre-pulls the postgres:13-alpine image used by AbstractIntegrationTest.
#
# NOTE: Image pulls require the environment's Network access to allow Docker Hub's
# blob host `production.cloudfront.docker.com`. With the default Trusted policy that
# host is blocked, so the pre-pull below will warn (not fail) and integration tests
# will be unable to start until the network policy is updated to Custom/Full.
set -euo pipefail

# Only run in the remote (Claude Code on the web) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Persist Testcontainers config for every shell/Gradle invocation this session.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export TESTCONTAINERS_RYUK_DISABLED=true' >> "$CLAUDE_ENV_FILE"
fi
export TESTCONTAINERS_RYUK_DISABLED=true

# 1. Ensure the Docker daemon is up.
if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon not running; starting dockerd..."
  sudo dockerd >/tmp/dockerd.log 2>&1 &
  for _ in $(seq 1 20); do
    if docker info >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
fi

if ! docker info >/dev/null 2>&1; then
  echo "WARNING: Docker daemon failed to start. Integration tests will not run." >&2
  echo "Last lines of /tmp/dockerd.log:" >&2
  tail -n 20 /tmp/dockerd.log >&2 || true
  exit 0
fi
echo "Docker daemon is running."

# 2. Pre-pull the Postgres image used by the integration tests (best-effort).
if ! docker image inspect postgres:13-alpine >/dev/null 2>&1; then
  echo "Pulling postgres:13-alpine..."
  if ! docker pull postgres:13-alpine; then
    echo "WARNING: Could not pull postgres:13-alpine." >&2
    echo "This usually means the Network access policy blocks Docker Hub's blob host." >&2
    echo "Set Network access to Custom and allow 'production.cloudfront.docker.com', or use Full." >&2
  fi
else
  echo "postgres:13-alpine already present."
fi

echo "SessionStart hook complete."
