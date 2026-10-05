#!/usr/bin/env bash
# The spec's smoke test, through the edge: proves TLS, the proxying, Caddy's routing and relay -> MAL.
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"
# Every check runs and reports, pass or fail, and the exit code is decided at the end. lib.sh's errexit would instead
# end the script silently at the first failing grep or `[`.
set +e

fail=0
check() { # description, ok(0/1)
	if [ "$2" = 0 ]; then echo "  ok   $1"; else echo "  FAIL $1"; fail=1; fi
}
hdr="$(mktemp)"; trap 'rm -f "$hdr" "$hdr.body"' EXIT

fetch() { curl -sS --max-time 20 -D "$hdr" -o "$hdr.body" "$@"; }
no_coop() { ! grep -qi '^cross-origin-opener-policy:' "$hdr"; }

fetch "$ACT_URL/" && grep -q '<html' "$hdr.body"; check "/ serves index.html" $?
no_coop; check "/ has no Cross-Origin-Opener-Policy" $?

fetch "$ACT_URL/oauth/callback?x=1" && grep -q '<html' "$hdr.body"; check "/oauth/callback?x=1 serves index.html" $?
grep -qi '^referrer-policy: no-referrer' "$hdr"; check "/oauth/callback sends Referrer-Policy: no-referrer" $?
no_coop; check "/oauth/callback has no Cross-Origin-Opener-Policy" $?

fetch "$ACT_URL/webApp.js" && grep -qi '^content-type: .*javascript' "$hdr" && ! grep -q '<html' "$hdr.body"
check "/webApp.js is JavaScript, not HTML" $?
no_coop; check "/webApp.js has no Cross-Origin-Opener-Policy" $?

fetch "$ACT_URL/privacy" && grep -qi 'privacy policy' "$hdr.body"; check "/privacy serves the policy" $?
no_coop; check "/privacy has no Cross-Origin-Opener-Policy" $?

# An anonymous call is 403 from MAL; a bogus bearer token gets the 401 that proves relay -> MAL.
code="$(fetch -H 'Authorization: Bearer smoke' "$ACT_URL/mal/v2/anime?q=x" -w '%{http_code}')"
# Read into a variable first: a $(...) in check's description would reset $? before check sees it.
[ "$code" = 401 ]; check "/mal/v2/anime?q=x with a bogus token is MAL's 401 (got $code)" $?
no_coop; check "/mal has no Cross-Origin-Opener-Policy" $?

exit "$fail"
