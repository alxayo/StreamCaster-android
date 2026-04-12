#!/usr/bin/env bash
# StreamCaster build script for macOS and Linux.
# Usage: ./build.sh [variant]
#   variant: fossDebug (default), gmsDebug, fossRelease, gmsRelease, all
set -euo pipefail

VARIANT="${1:-fossDebug}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLEW="$PROJECT_DIR/gradlew"
ARTIFACTS_DIR="$PROJECT_DIR/artifacts"

mkdir -p "$ARTIFACTS_DIR"
chmod +x "$GRADLEW"

echo "=== StreamCaster Build ==="
echo "Variant: $VARIANT"
echo ""

build_variant() {
    local flavor="$1"
    local type="$2"
    local flavor_cap="$3"
    local type_cap="$4"
    local task="assemble${flavor_cap}${type_cap}"
    local apk_path="app/build/outputs/apk/${flavor}/${type}/app-${flavor}-${type}.apk"
    local artifact_name="streamcaster-${flavor}-${type}.apk"

    echo "Building ${flavor} ${type}..."
    "$GRADLEW" --no-daemon ":app:$task" -q
    cp "$PROJECT_DIR/$apk_path" "$ARTIFACTS_DIR/$artifact_name"
    echo "  -> artifacts/$artifact_name"
}

case "$VARIANT" in
    fossDebug)    build_variant foss debug Foss Debug ;;
    gmsDebug)     build_variant gms debug Gms Debug ;;
    fossRelease)  build_variant foss release Foss Release ;;
    gmsRelease)   build_variant gms release Gms Release ;;
    all)
        build_variant foss debug Foss Debug
        build_variant gms debug Gms Debug
        build_variant foss release Foss Release
        build_variant gms release Gms Release
        ;;
    *)
        echo "Unknown variant: $VARIANT"
        echo "Options: fossDebug, gmsDebug, fossRelease, gmsRelease, all"
        exit 1
        ;;
esac

echo ""
echo "Running unit tests..."
"$GRADLEW" --no-daemon testFossDebugUnitTest -q
echo "Tests passed."

echo ""
echo "=== Build complete ==="
ls -lh "$ARTIFACTS_DIR"/*.apk 2>/dev/null
