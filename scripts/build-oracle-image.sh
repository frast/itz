#!/usr/bin/env bash
set -euo pipefail

installer="${1:?Usage: $0 /path/to/LINUX.X64_193000_db_home.zip}"
if [[ ! -f "$installer" ]]; then
  echo "Oracle 19c installer not found: $installer" >&2
  exit 1
fi

expected_sha256="ba8329c757133da313ed3b6d7f86c5ac42cd9970a28bf2e6233f3235233aa8d8"
actual_sha256="$(sha256sum "$installer" | awk '{print $1}')"
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
  echo "Unexpected Oracle 19c installer checksum: $actual_sha256" >&2
  exit 1
fi

workspace="${HOME}/.cache/itz-oracle-docker-images"
oracle_docker_images_commit="7b783da7cf0204691c7576226bce4f13ce7816d3"
if [[ ! -d "$workspace/.git" ]]; then
  git init -q "$workspace"
  git -C "$workspace" remote add origin https://github.com/oracle/docker-images.git
fi
git -C "$workspace" fetch -q --depth 1 origin "$oracle_docker_images_commit"
git -C "$workspace" checkout -q --detach FETCH_HEAD

target="$workspace/OracleDatabase/SingleInstance/dockerfiles/19.3.0/LINUX.X64_193000_db_home.zip"
cp "$installer" "$target"
cd "$workspace/OracleDatabase/SingleInstance/dockerfiles"
./buildContainerImage.sh -v 19.3.0 -e -t oracle/database:19.3.0-ee
