#!/usr/bin/env sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
PROPERTIES_FILE="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "Missing Maven wrapper properties: $PROPERTIES_FILE" >&2
  exit 1
fi

distribution_url=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES_FILE" | head -n 1)
if [ -z "$distribution_url" ]; then
  echo "distributionUrl is required in $PROPERTIES_FILE" >&2
  exit 1
fi

maven_version=$(printf '%s\n' "$distribution_url" | sed -n 's|.*/apache-maven-\([0-9][^/]*\)-bin\.zip|\1|p')
if [ -z "$maven_version" ]; then
  echo "Cannot determine Maven version from distributionUrl: $distribution_url" >&2
  exit 1
fi

MAVEN_HOME="$WRAPPER_DIR/apache-maven-$maven_version"
ARCHIVE="$WRAPPER_DIR/apache-maven-$maven_version-bin.zip"

if [ ! -x "$MAVEN_HOME/bin/mvn" ] || ! ls "$MAVEN_HOME"/boot/plexus-classworlds-*.jar >/dev/null 2>&1; then
  mkdir -p "$WRAPPER_DIR"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$distribution_url" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$distribution_url" -O "$ARCHIVE"
    else
      echo "curl or wget is required to download Maven" >&2
      exit 1
    fi
  fi
  rm -rf "$MAVEN_HOME"
  (
    cd "$WRAPPER_DIR"
    if command -v jar >/dev/null 2>&1; then
      jar xf "$ARCHIVE"
    elif command -v unzip >/dev/null 2>&1; then
      unzip -q "$ARCHIVE"
    else
      echo "jar or unzip is required to extract Maven" >&2
      exit 1
    fi
  )
  chmod +x "$MAVEN_HOME/bin/mvn"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
