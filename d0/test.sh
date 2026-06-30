#!/bin/bash

set -e

basedir=$(dirname $0)

cd "$basedir"

lein midje
