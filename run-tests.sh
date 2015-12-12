#!/bin/bash

WORKSPACE="target/simple/workspace"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [ ! -d "$WORKSPACE" ]
then
    mkdir -p $WORKSPACE
    git clone . $WORKSPACE
fi

cd $WORKSPACE
git checkout $CURRENT_BRANCH
git pull

lein test
