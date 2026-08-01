#!/usr/bin/env bash
# Start Sarvasiksha AI locally. OpenAI key is never written into the repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${SERVER_PORT:-8090}"
CRDS_FILE="${1:-}"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  if [[ -z "$CRDS_FILE" || ! -f "$CRDS_FILE" ]]; then
    echo "Usage: OPENAI_API_KEY=... $0" >&2
    echo "   or: $0 /path/to/.crds   # extracts 'personal openai key:' line" >&2
    exit 1
  fi
  OPENAI_API_KEY="$(python3 -c "
import pathlib,re,sys
t=pathlib.Path(sys.argv[1]).read_text()
m=re.search(r'personal openai key:\s*(sk-[^\s]+)', t, re.I)
if not m:
    sys.exit('personal openai key not found in crds file')
print(m.group(1), end='')
" "$CRDS_FILE")"
  export OPENAI_API_KEY
fi

JAR="$ROOT/target/sarvashiksha-ai-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi

echo "Starting on http://localhost:${PORT} (openaiConfigured will show on /api/v1/health)"
exec java -jar "$JAR" --server.port="$PORT"
