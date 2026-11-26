#!/bin/sh
# Test stand-in for pg_dump: writes all its arguments to the path after --file. Fails when PGPASSWORD is missing,
# which proves the password is passed through the environment.
[ -n "$PGPASSWORD" ] || { echo "no password" >&2; exit 2; }
ARGS="$*"
while [ $# -gt 0 ]; do
  if [ "$1" = "--file" ]; then printf 'FAKE-DUMP %s\n' "$ARGS" > "$2"; exit 0; fi
  shift
done
echo "no --file argument" >&2
exit 1
