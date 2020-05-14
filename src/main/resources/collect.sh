#!/bin/bash

# Collect and process output of batch runs
if [ -z "$1" ]
  then
    echo "Must pass city name EXACTLY as written in infections.txt"
    echo "e.g.: collect.sh Berlin"
    exit 1
fi

mkdir tmp

echo "Filtering output..."

for f in output/*/*.infections.txt; do
    name=$(basename "$f")
    head -1 $f > tmp/$name.csv
    grep $1 $f >> tmp/$name.csv
done

for f in output/*/*.restrictions.txt; do
    name=$(basename "$f")
    cp $f tmp/$name.csv
done

echo "Creating zip file..."

zip --junk-paths summaries.zip tmp/*.csv

rm -r tmp