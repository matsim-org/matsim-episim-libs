
import argparse
import os
from os import path
from glob import glob

import pandas as pd

#%%

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Generate summary from calibration output.")
    parser.add_argument("trial", metavar='TRIAL', type=str, nargs=1, help="Trial to analyze")
    parser.add_argument("--output", type=str, help="Scenario module used for calibration", default="./output-calibration")
    parser.add_argument("--district", type=str, default="Köln", help="District to extract data for.")

    args = parser.parse_args()
    
    out = path.join(args.output, args.trial[0], "run_*")
    
    dfs = []
    
    for run in glob(out):
           
        f = glob(path.join(run, "*.infections.txt"))[0]
        scenario = path.basename(f).split(".")[0].replace("run", "")

        print("Analyzing ", scenario)
        
        df = pd.read_csv(f, sep="\t", parse_dates=[2])
        df = df[df.district == args.district]
        
        df["cases"] = df.nInfectedCumulative.diff(1)

        df = df.reset_index()
        df.cases.loc[0] = df.nInfectedCumulative[0]
       
        s = df.groupby(pd.Grouper(key='date', freq='W-SUN')).agg(cases=("cases", "sum"))      
        s.cases = 100000* s.cases / 919944
        s["run"] = scenario
        
        dfs.append(s)
        
        
    pd.concat(dfs).to_csv(args.trial[0] + ".csv")
