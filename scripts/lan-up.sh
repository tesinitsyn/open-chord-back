#!/usr/bin/env sh
set -eu

lan_ip="${OPENCHORD_LAN_IP:-}"

if [ -z "$lan_ip" ] && command -v ipconfig >/dev/null 2>&1; then
  lan_ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
fi

if [ -z "$lan_ip" ] && command -v hostname >/dev/null 2>&1; then
  lan_ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
fi

if [ -z "$lan_ip" ]; then
  echo "Could not detect a LAN address. Set OPENCHORD_LAN_IP and run again." >&2
  exit 1
fi

export PUBLIC_BASE_URL="http://${lan_ip}:8080"

echo "Starting OpenChord at ${PUBLIC_BASE_URL}"
echo "Use this address in the iOS app's Server settings."
docker compose up --build
