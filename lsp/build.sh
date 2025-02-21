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

lein uberjar

mkdir -p "$basedir/bin"
cp $basedir/target/y0lsp-*-standalone.jar "$basedir/bin/y0lsp.jar"
