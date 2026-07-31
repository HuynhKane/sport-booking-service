#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
	echo "Usage: $0 <base-url> <expected-commit>" >&2
	exit 2
fi

base_url=${1%/}
expected_commit=$2
timeout_seconds=${SMOKE_TIMEOUT_SECONDS:-600}
interval_seconds=${SMOKE_INTERVAL_SECONDS:-10}
deadline=$((SECONDS + timeout_seconds))
info_body=''

echo "Waiting for commit ${expected_commit} at ${base_url}"

while (( SECONDS < deadline )); do
	if info_body=$(curl --fail --silent --show-error \
		--connect-timeout 10 --max-time 20 "${base_url}/actuator/info" 2>/dev/null); then
		if [[ $info_body == *"\"commit\":\"${expected_commit}\""* ]]; then
			break
		fi
	fi
	sleep "$interval_seconds"
done

if [[ $info_body != *"\"commit\":\"${expected_commit}\""* ]]; then
	echo "Timed out waiting for the expected commit" >&2
	exit 1
fi

if [[ $info_body != *'"name":"sport-booking-service"'* ]]; then
	echo "The deployed service identity is incorrect" >&2
	exit 1
fi

health_body=$(curl --fail --silent --show-error \
	--connect-timeout 10 --max-time 20 "${base_url}/actuator/health/readiness")

if [[ $health_body != *'"status":"UP"'* ]]; then
	echo "The deployed service is not ready" >&2
	exit 1
fi

echo "Smoke test passed for commit ${expected_commit}"
