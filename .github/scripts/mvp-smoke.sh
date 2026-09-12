#!/usr/bin/env bash
set -euo pipefail

MC_VERSION="$1"
MODULE="$2"
IMAGE="$3"
SERVER_TYPE="$4"
FIXTURE_ROOT="${5:-mvp-content}"
PACK_TARGET="mc-$MC_VERSION"
NAME="voxelcore-smoke-${MC_VERSION//./-}"
DATA_DIR="$PWD/.smoke/${MC_VERSION}"
FIXTURE_ITEMS="$PWD/$FIXTURE_ROOT/voxeltest/content/items.yml"
CONTAINER_ITEMS="/data/plugins/VoxelCore/content/voxeltest/content/items.yml"

cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

rm -rf "$DATA_DIR"
mkdir -p "$DATA_DIR/plugins/VoxelCore/content"

JAR="$(find "$MODULE/target" -maxdepth 1 -type f -name 'VoxelCore-*.jar' | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "No VoxelCore distribution jar found under $MODULE/target" >&2
  exit 1
fi

cp "$JAR" "$DATA_DIR/plugins/VoxelCore.jar"
cp -R "$PWD/$FIXTURE_ROOT/." "$DATA_DIR/plugins/VoxelCore/content/"

docker run -d --name "$NAME" \
  -e EULA=TRUE \
  -e TYPE="$SERVER_TYPE" \
  -e VERSION="$MC_VERSION" \
  -e ONLINE_MODE=FALSE \
  -e ENABLE_RCON=TRUE \
  -e RCON_PASSWORD=voxelcore-smoke \
  -e MEMORY=1G \
  -v "$DATA_DIR:/data" \
  "$IMAGE" >/dev/null

READY=0
for _ in $(seq 1 180); do
  LOGS="$(docker logs "$NAME" 2>&1 || true)"
  if grep -q 'VOXELCORE_READY revision=1 items=3' <<<"$LOGS"; then
    READY=1
    break
  fi
  if ! docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null | grep -q true; then
    echo "Minecraft container exited before VoxelCore became ready" >&2
    echo "$LOGS" >&2
    exit 1
  fi
  sleep 2
done

if [[ "$READY" != 1 ]]; then
  echo "Timed out waiting for VoxelCore readiness marker" >&2
  docker logs "$NAME" >&2 || true
  exit 1
fi

CONTENT_INFO="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke 'voxelcore admin content info')"
echo "$CONTENT_INFO"
grep -q 'content revision 1: 3 items' <<<"$CONTENT_INFO"

VERIFY="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke 'voxelcore admin item verify voxeltest:red_item')"
echo "$VERIFY"
grep -q 'VOXELCORE_ITEM_VERIFY_OK id=voxeltest:red_item' <<<"$VERIFY"

PACK_VALIDATE="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke "voxelcore admin pack validate $PACK_TARGET")"
echo "$PACK_VALIDATE"
grep -q "pack valid for $PACK_TARGET" <<<"$PACK_VALIDATE"

PACK_BUILD="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke "voxelcore admin pack build $PACK_TARGET")"
echo "$PACK_BUILD"
grep -q "pack built for $PACK_TARGET" <<<"$PACK_BUILD"
test -f "$DATA_DIR/plugins/VoxelCore/build/resource-packs/$PACK_TARGET.zip"

docker exec -i "$NAME" sh -c "cat > '$CONTAINER_ITEMS'" <<'BROKEN'
items:
  red_item:
    extends: missing_parent
    material: minecraft:paper
BROKEN

FAILED_RELOAD="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke 'voxelcore admin content reload')"
echo "$FAILED_RELOAD"
grep -q 'reload failed; revision 1 remains active' <<<"$FAILED_RELOAD"

VERIFY_AFTER_FAILURE="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke 'voxelcore admin item verify voxeltest:red_item')"
echo "$VERIFY_AFTER_FAILURE"
grep -q 'VOXELCORE_ITEM_VERIFY_OK id=voxeltest:red_item' <<<"$VERIFY_AFTER_FAILURE"

docker exec -i "$NAME" sh -c "cat > '$CONTAINER_ITEMS'" < "$FIXTURE_ITEMS"
SUCCESS_RELOAD="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke 'voxelcore admin content reload')"
echo "$SUCCESS_RELOAD"
grep -q 'content reloaded: revision 2, 3 items' <<<"$SUCCESS_RELOAD"

FINAL_VERIFY="$(docker exec "$NAME" rcon-cli --password voxelcore-smoke 'voxelcore admin item verify voxeltest:child_item')"
echo "$FINAL_VERIFY"
grep -q 'VOXELCORE_ITEM_VERIFY_OK id=voxeltest:child_item' <<<"$FINAL_VERIFY"

echo "VoxelCore MVP smoke test passed for Minecraft $MC_VERSION using $FIXTURE_ROOT"
