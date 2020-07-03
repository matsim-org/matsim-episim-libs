#!/usr/bin/env python
# -*- coding: utf-8 -*-

import argparse
import re
import sqlite3
from datetime import date, timedelta
from os import listdir, path
from shutil import copy

PATTERN = re.compile(r"--start (\d{4}-\d{2}-\d{2})")

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Prepare next calibration step.")
    parser.add_argument("--update", type=bool, default=False, help="Update the start script with new date")
    parser.add_argument("--study", type=str, default="ci_correction", help="Objective name")
    parser.add_argument("--param", type=str, help="ciCorrection")
    parser.add_argument("--script", type=str, default="s_calibrate.sh", help="Name of the start script to parse for the date")

    args = parser.parse_args()

    current_date = ""
    if path.exists(args.script):
        with open(args.script) as f:
            match = PATTERN.findall(f.read())
            if not match:
                print("No start date found in the script")
                exit(1)

            current_date = match[0]

    db = sqlite3.connect("calibration.db")

    study_name = "ci_correction_" + current_date

    study = db.execute("SELECT * from studies WHERE study_name=?", (study_name,)).fetchone()

    if not study:
        print("No study with date %s found" % date)
        exit(1)

    trials = db.execute("SELECT * from trials WHERE study_id=? AND state='COMPLETE'", (study[0],)).fetchall()
    trials = sorted(trials, key=lambda x: x[4])

    params = []
    for t in trials[0:5]:
        param = db.execute("SELECT * from trial_params WHERE param_name=? AND trial_id=?", (args.param, t[0])).fetchone()
        params.append((t, param))

    # Choose median of best 5 trials
    params = sorted(params, key=lambda x: x[1][3])
    best = params[3]
    print("Best Trial: " + str(best))

    print("Best params: %s" % [x[1][3] for x in params])

    n = best[0][1]
    value = best[1][3]

    print(f"Trial n={n} with error={best[0][4]} and {args.param}={value}")

    next_date = date.fromisoformat(current_date) + timedelta(days=14)

    folder = "output-calibration-" + current_date + "/%d/run0/" % n
    files = listdir(folder)

    snapshot = next(filter(lambda f: str(next_date) in f, files))

    if not snapshot:
        exit(1)

    print("Found snapshot " + snapshot)

    copy(path.join(folder, snapshot), "episim-snapshot-%s.zip" % next_date)

    script = open(args.script).read()

    next_script = PATTERN.sub(str(next_date), script)

    print(f"Next date is {next_date}")

    if args.update:
        print(f"Setting next date in script")
        open(args.script, "w").write(next_script)

    print("Done")
