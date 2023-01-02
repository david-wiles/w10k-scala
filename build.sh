#!/bin/bash

set -eo pipefail

sbt assembly

docker build -t w10k-scala:v1 .
