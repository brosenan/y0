#!/bin/bash

set -e

basedir=$(dirname $0)
root=$(realpath "$basedir/y0_test")

all_y0_files=$(find $root -name "*.y0")

lein run -m y0.main -p "$root" $all_y0_files
lein run -m y0.main -p "$root" -c "$basedir/lang-conf.edn" -s $basedir/doc/*-spec.md

$basedir/create-docs.sh
