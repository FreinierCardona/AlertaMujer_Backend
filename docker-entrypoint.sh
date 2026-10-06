#!/bin/sh
set -eu

require_environment() {
    variable_name="$1"
    variable_value="$(printenv "$variable_name" 2>/dev/null || true)"
    if [ -z "$variable_value" ]; then
        echo "Required container configuration is missing: $variable_name" >&2
        exit 1
    fi
}

require_environment SPRING_DATASOURCE_URL
require_environment SPRING_DATASOURCE_USERNAME
require_environment SPRING_DATASOURCE_PASSWORD
require_environment JWT_SECRET
require_environment CORS_ALLOWED_ORIGINS
require_environment FCM_CREDENTIALS_PATH
require_environment EVIDENCE_STORAGE_PATH

if [ "$SPRING_DATASOURCE_USERNAME" != "alertamujer_app" ]; then
    echo "The JDBC user must be alertamujer_app" >&2
    exit 1
fi

mkdir -p "$EVIDENCE_STORAGE_PATH"
if [ ! -w "$EVIDENCE_STORAGE_PATH" ]; then
    echo "The evidence storage path is not writable" >&2
    exit 1
fi

exec java -jar /app/app.jar --spring.profiles.active=docker
