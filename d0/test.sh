#!/bin/bash

set -e

basedir=$(dirname $0)

cd "$basedir"

lein midje

root=$(realpath .)

lein run -m y0.main -p "$root" -c lang-conf.edn -s doc/*-spec.md
