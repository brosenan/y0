#!/bin/bash
set -e

basedir=$(dirname $0)

addons_dir="$basedir/src/y0lsp/addons"
addons=$(cd $addons_dir && ls -1 | sed "s/.clj$//")
all_addons_file="$basedir/src/y0lsp/all_addons.clj"

echo "(ns y0lsp.all-addons" > "$all_addons_file"
echo "  (:require" >> "$all_addons_file"
for addon in $addons; do
  echo "    [y0lsp.addons.$addon]" >> "$all_addons_file"
done
echo "  ))" >> "$all_addons_file"

(cd $basedir && lein uberjar)

bindir="$basedir/bin"
mkdir -p "$bindir"

jarfile="$bindir/y0lsp.jar"
cp $basedir/target/y0lsp-*-standalone.jar "$jarfile"

(cd $basedir/.. && lein uberjar)

y0jarfile="$bindir/y0.jar"
cp $basedir/../target/y0-*-standalone.jar "$y0jarfile"

echo "$jarfile built successfully."
"$basedir/create-docs.sh"