#!/bin/bash

# Collect and process output of batch runs
if [ -z "$1" ]
  then
    echo "Must pass city name EXACTLY as written in infections.txt"
    echo "e.g.: collect.sh Berlin"
    exit 1
fi

# Copies output and appends additional file ending
copy_output() {
    if test -f "$1"; then
        name=$(basename "$1")
        cp $1 $2/$name$3
    fi
}

cwd=$(pwd)
tmp="$cwd/tmp/summaries"

mkdir -p "$tmp"

echo "Filtering output..."

# Function to aggregate a single run
# First argument is infection file
aggregate_run() {
    name=$(basename "$1")
    dir=$(dirname "$1")

    run="${name%%.*}"
    mkdir "$tmp/$run"

    cd "$dir" || exit

    # Copy and grep the infections file
    head -1 $name > $tmp/$run/$name.csv
    grep $1 $name >> $tmp/$run/$name.csv

    # Copy other output files
    copy_output *.restrictions.txt $tmp/$run .csv
    copy_output *.rValues.txt $tmp/$run .csv
    copy_output *.diseaseImport.tsv $tmp/$run
    copy_output *.infectionsPerActivity.txt $tmp/$run .tsv

    for OUTPUT in *.post.*.*; do
        copy_output $OUTPUT $tmp/$run
    done

    zip $tmp/$run.zip --junk-paths -r $tmp/$run

    rm -r "${tmp:?}/$run"

    cd "$cwd" || exit
}

# Iterate over all runs, with an infections file
for f in output/*/*.infections.txt; do
    aggregate_run "$f"
done

wait

echo "Creating zip file..."

cd "$cwd" || exit
cp _info.txt metadata.yaml tmp

cd "$cwd/tmp" || exit

zip "$cwd/summaries.zip" -r ./*

cd "$cwd" || exit

rm -r tmp