import argparse
import numpy as np
import pandas as pd
import sqlite3
from glob import glob
from os import path


# %%

def best(f, study_name="strain", p="infectiousness", top=3):
    """ Select best trial from  database """

    db = sqlite3.connect(f)
    study = db.execute("SELECT * from studies WHERE study_name=?", (study_name,)).fetchone()
    trials = db.execute("SELECT * from trials WHERE study_id=? AND state='COMPLETE'", (study[0],)).fetchall()
    trials = sorted(trials, key=lambda x: x[4])

    params = []
    for t in trials[0:top]:
        param = db.execute("SELECT * from trial_params WHERE param_name=? AND trial_id=?", (p, t[0])).fetchone()
        params.append((t, param))

    # Choose median of best top trials
    params = sorted(params, key=lambda x: x[1][3])
    best = params[int((top - 1) / 2)]

    n = best[0][1]
    value = best[1][3]
    err = best[0][4]

    return n, value, err


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Generate summary from weekly strains calibration.")
    parser.add_argument("--output", type=str, help="Output CSV", default="result.csv")
    parser.add_argument("--strain", type=str, help="Strain", default="ALPHA")
    parser.add_argument("--district", type=str, default="Köln", help="District to extract data for.")

    args = parser.parse_args()

    out = glob("calibration*.db")

    trials = []

    for db in out:

        date = db.replace(".db", "").replace("calibration", "")

        print("Analyzing", db, "for date", date)

        n, value, err = best(db)

        print("Best:", n, "with value:", value, "and error", err)

        p = "output-%s/%d/run_*/run*.strains.tsv" % (date, n)

        dfs = []

        for f in glob(p):
            run = path.basename(f).split(".")[0].replace("run", "")

            df = pd.read_csv(f, sep="\t", parse_dates=[1])
            total = np.sum(df.to_numpy()[:, 2:], axis=1)
            total[total == 0] = np.nan

            # replace 0 for the division to change it back later

            df["total"] = total
            df["share"] = (df[args.strain] / df.total).fillna(0)

            s = df.groupby(pd.Grouper(key='date', freq='W-SUN')).agg(share=("share", "mean"))
            s = s[s.index >= "2021-01-03"]


            df = pd.read_csv(f.replace("strains.tsv", "infections.txt"), sep="\t", parse_dates=[2])
            df = df[df.district == args.district]

            df["cases"] = df.nInfectedCumulative.diff(1)

            df = df.reset_index()
            df.cases.loc[0] = df.nInfectedCumulative[0]

            s2 = df.groupby(pd.Grouper(key='date', freq='W-SUN')).agg(cases=("cases", "sum"))
            s2 = s2[s2.index >= "2021-01-03"]
            s2.cases = 100000* s2.cases / 919944

            s = s.merge(s2, left_index=True, right_index=True)


            s["trial"] = date
            s["run"] = run

            dfs.append(s)

        trials.append(pd.concat(dfs))

    pd.concat(trials).to_csv(args.output)
