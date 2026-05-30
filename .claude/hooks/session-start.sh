#!/bin/bash
# SessionStart hook for Claude Code on the web.
#
# Prepares the environment so the Testcontainers-based integration tests can run:
#   1. Ensures a JDK 17 toolchain is installed. The Gradle build pins
#      languageVersion 17 (build.gradle), but the web image only ships JDK 21,
#      so :compileKotlin fails before any test runs without this.
#   2. Ensures the Docker daemon is running (web containers don't start it by default).
#   3. Disables the Testcontainers Ryuk reaper so it never needs to pull
#      testcontainers/ryuk (the container is ephemeral, so no reaping is needed).
#   4. Pre-pulls the postgres:13-alpine image used by AbstractIntegrationTest.
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

# 1. Ensure a JDK 17 toolchain is available for Gradle to auto-detect.
#    Gradle scans /usr/lib/jvm; install OpenJDK 17 there if it is missing.
have_jdk17() {
  local d
  for d in /usr/lib/jvm/*; do
    if [ -x "$d/bin/javac" ] && "$d/bin/javac" -version 2>&1 | grep -q 'javac 17\.'; then
      return 0
    fi
  done
  return 1
}

if have_jdk17; then
  echo "JDK 17 toolchain already present."
else
  echo "JDK 17 not found; installing openjdk-17-jdk-headless via apt..."
  # The JDK is served from the Ubuntu archive (archive.ubuntu.com /
  # security.ubuntu.com), which the default Trusted network policy already
  # allows, so no network-policy change is required.
  #
  # Refresh the package index first: the preinstalled index is stale and points
  # at openjdk-17 builds no longer in the pool, which makes a bare `apt-get
  # install` 404. `apt-get update` exits non-zero here because a couple of
  # unrelated third-party PPAs (deadsnakes, ondrej/php) are blocked by the
  # network policy, but the Ubuntu archive entries still refresh, so its exit
  # code is ignored.
  apt-get update >/tmp/jdk17-update.log 2>&1 || true
  if apt-get install -y -q openjdk-17-jdk-headless >/tmp/jdk17-install.log 2>&1 && have_jdk17; then
    echo "Installed JDK 17 toolchain."
  else
    echo "WARNING: failed to install openjdk-17-jdk-headless via apt." >&2
    echo "Last lines of /tmp/jdk17-install.log:" >&2
    tail -n 20 /tmp/jdk17-install.log >&2 || true
    echo "If this is a network (apt download) failure, allow these hosts in the" >&2
    echo "Network access policy: 'archive.ubuntu.com' and 'security.ubuntu.com'." >&2
  fi
fi

# 2. Ensure the Docker daemon is up.
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

# 3. Pre-pull the Postgres image used by the integration tests (best-effort).
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
