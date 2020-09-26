#!/usr/bin/env python
# -*- coding: utf-8 -*-

import zipfile
from os import path

import numpy as np
import pandas as pd

# %%

# Base folder where the _info is located.
folder = "."


def extract(f):
    ev = pd.read_csv(f, sep="\t")
    no_inf = set(ev.infected).difference(set(ev.infector))

    res = np.concatenate((np.zeros(len(no_inf)), np.sort(ev['infector'].value_counts().array)))
    return res


def extract_batch(array):
    print("Processing", len(array), "runs as batch")

    data = []

    for i, name in array.iteritems():
        run = info.loc[i].RunId

        f = path.join(folder, name, run + ".infectionEvents.txt")
        if not path.exists(f):
            continue

        res = extract(f)
        print("Processed array of", res.shape)
        data.append(res)

    print("Collected", len(data), "files")

    if not data:
        return

    return pd.DataFrame(data)


if __name__ == "__main__":

    info = pd.read_csv(path.join(folder, "_info.txt"), sep=";")

    # %%

    # Example for filtering that needs to be adapted to dataset
    df = info

    print("Processing:")
    print(df)

    # %%

    gb = [x for x in info.columns if x not in {"Config", "Output", "RunId", "RunScript", "seed"}]
    aggr = df.groupby(gb).agg(inf=('Output', extract_batch))

    # %%

    with zipfile.ZipFile("infections.zip", "w") as z:

        for index, data in aggr.itertuples():

            try:
                name = "_".join(str(f) for f in index)
            except TypeError:
                name = str(index)

            if data is not None:
                print("Writing", name)
                print(data)
                with z.open(name=name + "_aggr.npy", mode="w") as f:
                    np.save(f, data, allow_pickle=False)
