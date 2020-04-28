#!/bin/bash

# Collect and process output
if [ -z "$1" ]
  then
    echo "No target district supplied"
    exit 1
fi

mkdir tmp

echo "Filtering output..."

for f in output/*/*.infections.txt; do
    name=$(basename "$f")
    grep -E "day|$1" $f > tmp/$name
done


echo "Creating zip file..."

cd tmp

zip ../summaries.zip *.txt