#!/bin/bash

set -eo pipefail

if [ -z "$1" ]; then
  echo "Usage: ./deploy.sh [domain]";
  exit 1
else
  export TF_VAR_domain=$1
fi

# Build jars
sbt assembly

# Create the VM
cd tf
terraform apply

# Copy executables to VM
cd ..
scp broadcast/target/scala-2.13/broadcast.jar "w10k-scala.$1":broadcast.jar
scp client2client/target/scala-2.13/client2client.jar "w10k-scala.$1":client2client.jar
