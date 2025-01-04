#!/bin/bash
set -e

basedir=$(dirname $0)
docdir=$basedir/doc
clj_test_files=$(find $basedir -name "*_test.clj")
awkfile=$basedir/../clj-to-md.awk

for file in $clj_test_files; do
    target=$docdir/$(basename $file | sed "s/_test.clj$//" | sed "s/.y0$//").md
    awk -f $awkfile $file > $target
done
