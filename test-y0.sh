#!/bin/bash

set -e

basedir=$(dirname $0)
root=$basedir/y0_test

all_y0_files=$(find $root -name "*.y0")
modules=$(for file in $all_y0_files; do rel=${file#"$root/"}; echo ${rel%".y0"}; done)

lein run -m y0.main -p "$root" $modules

$basedir/create-docs.sh