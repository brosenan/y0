#!/bin/bash

set -e

basedir=$(dirname $0)

cd "$basedir"

root=$(realpath .)

lein run -m y0.main -p "$root" -c lang-conf.edn -s doc/*-spec.md
lein run -m d0.main doc/*-d0spec.md
lein midje
