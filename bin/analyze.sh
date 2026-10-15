#!/bin/bash
# Runs the static analysis profiles. Each profile compiles with a different checker:
#   nullaway         - NullAway in JSpecify mode (packages are @NullMarked)
#   checkerframework - Checker Framework Nullness Checker (stricter)
#   errorprone       - Error Prone bug patterns
# Usage: bin/analyze.sh [profiles]   e.g. bin/analyze.sh "nullaway"
set -e

_profiles="$1"
if [ -z "$_profiles" ]; then
  _profiles="nullaway checkerframework errorprone"
fi

# Maven only warns about an unknown -P profile and then runs a plain build with no
# analysis, so a typo would otherwise look like a clean run.
_valid_profiles="checkerframework errorprone nullaway"
for profile in $_profiles; do
  if [[ " $_valid_profiles " != *" $profile "* ]]; then
    echo "analyze.sh: unknown profile '$profile'. Valid profiles: $_valid_profiles" >&2
    exit 2
  fi
done

_modules="${_run_modules:-flylite,flylite-maven-plugin}"

for profile in $_profiles; do
  echo ""
  echo "--------------------- Running $profile -----------------------"
  echo ""
  ./mvnw $MAVEN_CLI_OPTS clean install -pl "${_modules}" -am -P${profile},-format-validate -DskipTests -Dinvoker.skip -Dmaven.javadoc.skip
done

# Checker or the maven compiler leaves these files around
find . -name "javac.*.args" -print0 | xargs -0 rm -f
