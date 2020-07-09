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
    parser.add_argument("--study", type=str, default="ci_correction_", help="Objective name")
    parser.add_argument("--param", type=str, default="ciCorrection", help="Name of parameter to retrieve")
    parser.add_argument("--script", type=str, default="s_calibrate.sh", help="Name of the start script to parse for the date")
    parser.add_argument("--date", type=str, default="", help="Date of the study (needed for some)")
    parser.add_argument("--db", type=str, default="calibration.db", help="Path to database")
    parser.add_argument("--top", type=int, default=5, help="Compute median from the best top N trials")

    args = parser.parse_args()

    current_date = args.date
    if path.exists(args.script):
        with open(args.script) as f:
            match = PATTERN.findall(f.read())
            if not match:
                print("No start date found in the script")
                exit(1)

            current_date = match[0]

    if not path.exists(args.db):
        print("Database %s does not exist")
        exit(1)

    db = sqlite3.connect(args.db)

    study_name = args.study + current_date

    study = db.execute("SELECT * from studies WHERE study_name=?", (study_name,)).fetchone()

    if not study:
        print("No study with name %s" % study_name)
        print("Available:", db.execute("SELECT study_name from studies").fetchall())
        exit(1)

    trials = db.execute("SELECT * from trials WHERE study_id=? AND state='COMPLETE'", (study[0],)).fetchall()
    trials = sorted(trials, key=lambda x: x[4])

    print("Found %d complete trials" % len(trials))

    params = []
    for t in trials[0:args.top]:
        param = db.execute("SELECT * from trial_params WHERE param_name=? AND trial_id=?", (args.param, t[0])).fetchone()
        if not param:
            print("Param %s not found" % args.param)
            print("Available:", db.execute("SELECT param_name FROM trial_params WHERE trial_id=?", (t[0],)).fetchall())
            exit(1)

        params.append((t, param))

    # Choose median of best 5 trials
    params = sorted(params, key=lambda x: x[1][3])
    best = params[int((args.top - 1) / 2)]
    print("Best Trial: " + str(best))

    print("Best candidates")
    for x in params:
        print("%s=%s, error=%s" % (args.param, x[1][3], x[0][4]))

    n = best[0][1]
    value = best[1][3]

    print(f"Trial n={n} with error={best[0][4]} and {args.param}={value}")

    if not current_date:
        exit(0)

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
