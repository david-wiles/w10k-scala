#!/bin/bash

set -eo pipefail

sbt $1/assembly

docker build -t $1:0.2.3 $1/
