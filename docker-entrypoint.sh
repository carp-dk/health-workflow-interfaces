#!/bin/sh
set -e

CONFIG_DIR="${CONFIG_DIR:-/app/config}"
DATA_DIR="${DATA_DIR:-/app/data}"

mkdir -p "$CONFIG_DIR" "$DATA_DIR"

# Write a dev keys.yaml if none exists.
# Set HWF_API_KEY to override the default. Mount your own config/keys.yaml in production.
if [ ! -f "$CONFIG_DIR/keys.yaml" ]; then
  KEY="${HWF_API_KEY:-dev-local-key-change-in-production}"
  cat > "$CONFIG_DIR/keys.yaml" << EOF
keys:
  - key: "$KEY"
    userId: "dev"
    name: "Dev Default Key"
EOF
  echo "[hwf-server] No keys.yaml found — wrote dev default (key: $KEY)"
fi

export JAVA_OPTS="-Dconfig.dir=$CONFIG_DIR -Ddata.dir=$DATA_DIR -Dserver.port=${HWF_PORT:-8080}"

exec bin/server
