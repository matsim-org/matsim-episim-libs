#!/usr/bin/env python
# -*- coding: utf-8 -*-

from os import path

import numpy as np
import pandas as pd

# %%

# Base folder where the _info is located.
folder = "."

info = pd.read_csv(path.join(folder, "_info.txt"), sep=";")


def extract(array):
    print("Processing", len(array), "runs as batch")

    data = []

    for i, name in array.iteritems():
        run = info.loc[i].RunId

        f = path.join(folder, name, run + ".infectionEvents.txt")
        if not path.exists(f):
            #print("Skipped", f)
            continue

        ev = pd.read_csv(f, sep="\t")
        no_inf = set(ev.infected).difference(set(ev.infector))

        res = np.concatenate((np.zeros(len(no_inf)), np.sort(ev['infector'].value_counts().array)))
        print("Processed array of", res.shape)
        data.append(res)

    print("Collected", len(data), "files")

    if not data:
        return

    return pd.DataFrame(data)


# %%

# Example for filtering that needs to be adapted to dataset
df = info

print("Processing:")
print(df)

# %%

aggr = df.groupby(["alpha", "ci"]).agg(inf=('Output', extract))

# %%

for index, data in aggr.itertuples():

    try:
        name = "_".join(str(f) for f in index)
    except TypeError:
        name = str(index)

    if data is not None:
        print("Writing", name)
        print(data)
        f = path.join(folder, name + "_aggr")
        np.save(f, data, allow_pickle=False)
