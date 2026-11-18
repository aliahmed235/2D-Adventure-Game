#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"
run_tests=false
run_game=false
for argument in "$@"; do
    case "$argument" in
        --test) run_tests=true ;;
        --run) run_game=true ;;
        *) printf 'Usage: sh build.sh [--test] [--run]\n' >&2; exit 1 ;;
    esac
done
if [ -n "${JAVA_HOME:-}" ]; then
    compiler="$JAVA_HOME/bin/javac"
    java="$JAVA_HOME/bin/java"
    jar="$JAVA_HOME/bin/jar"
else
    compiler=javac
    java=java
    jar=jar
fi
mkdir -p build/classes build/test-classes
# Only these two fixed generated directories are cleaned.
find build/classes build/test-classes -type f -delete
find src -name '*.java' -print | sed 's/.*/"&"/' > build/sources.txt
"$compiler" --release 17 -encoding UTF-8 -d build/classes @build/sources.txt
if "$run_tests"; then
    find tests -name '*.java' -print | sed 's/.*/"&"/' > build/test-sources.txt
    "$compiler" --release 17 -encoding UTF-8 -cp 'build/classes:res:tests/resources' -d build/test-classes @build/test-sources.txt
    "$java" -Djava.awt.headless=true -cp 'build/test-classes:build/classes:res:tests/resources' tests.GameTests
fi
"$jar" --create --file build/true-adventure.jar --main-class main.Main -C build/classes . -C res .
cp build/true-adventure.jar game.jar
if [ -d dist ]; then cp build/true-adventure.jar dist/game.jar; fi
printf 'Built build/true-adventure.jar\n'
if "$run_game"; then "$java" -jar build/true-adventure.jar; fi
