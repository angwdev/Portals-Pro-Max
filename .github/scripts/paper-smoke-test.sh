#!/usr/bin/env bash
# Boots a real Paper server with the freshly built plugin and checks that it
# enables and that the basic commands run without errors.
#
# Usage: paper-smoke-test.sh <plugin.jar> [paper-version]
#
# Needs: curl, jq and a Java 25 runtime on the PATH (Paper 26.x requirement).
set -euo pipefail

PLUGIN_JAR="$(realpath "$1")"
PAPER_VERSION="${2:-26.3}"
WORK_DIR="$(mktemp -d)"
STARTUP_TIMEOUT=300

echo "Resolving the latest stable Paper ${PAPER_VERSION} build"
BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds/latest")"
PAPER_URL="$(echo "$BUILD_JSON" | jq -r '.downloads["server:default"].url')"
PAPER_SHA="$(echo "$BUILD_JSON" | jq -r '.downloads["server:default"].checksums.sha256')"
echo "Paper build $(echo "$BUILD_JSON" | jq -r '.id'): ${PAPER_URL}"

mkdir -p "${WORK_DIR}/plugins"
curl -fsSL "$PAPER_URL" -o "${WORK_DIR}/paper.jar"
echo "${PAPER_SHA}  ${WORK_DIR}/paper.jar" | sha256sum -c -
cp "$PLUGIN_JAR" "${WORK_DIR}/plugins/"

cd "$WORK_DIR"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
server-port=25599
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
PROPS

# Keep the servers stdin open so commands can be sent to it
mkfifo stdin
exec 3<>stdin

java -Xmx1G -jar paper.jar --nogui <&3 > server.log 2>&1 &
SERVER_PID=$!

cleanup() {
  kill "$SERVER_PID" 2>/dev/null || true
  echo "---------------- server.log ----------------"
  cat "${WORK_DIR}/server.log" || true
}
trap cleanup EXIT

echo "Waiting for the server to start (max ${STARTUP_TIMEOUT}s)"
for ((i = 0; i < STARTUP_TIMEOUT; i++)); do
  if grep -q 'Done (' server.log; then
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "::error::The server stopped before it finished starting"
    exit 1
  fi
  sleep 1
done
grep -q 'Done (' server.log || { echo "::error::Server start timed out"; exit 1; }

send() {
  echo "> $1"
  echo "$1" >&3
  sleep 3
}

send "portal version"
send "portal list"
send "desti list"
send "portal reload"
send "portal help"
send "stop"

for ((i = 0; i < 60; i++)); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done

fail=0
check() {
  if ! grep -q "$1" server.log; then
    echo "::error::Expected to find '$1' in the server log"
    fail=1
  fi
}
check_absent() {
  if grep -qiE "$1" server.log; then
    echo "::error::Found '$1' in the server log"
    grep -iE "$1" server.log | head -20
    fail=1
  fi
}

check 'Enabling AdvancedPortals'
check_absent 'Error occurred while enabling AdvancedPortals'
check_absent 'Could not load .*plugins/.*advanced-portals'
check_absent 'Unhandled exception executing command .*(portal|desti)'
check_absent 'at com\.sekwah\.advancedportals'

exit "$fail"
