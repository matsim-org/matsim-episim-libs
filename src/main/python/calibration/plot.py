#!/usr/bin/env python
# -*- coding: utf-8 -*-

import logging
from collections import defaultdict

import pandas as pd
import plotly
import plotly.express as px
import plotly.graph_objects as go
from optuna.trial import TrialState

logger = logging.getLogger("plot")


def to_df(study):
    """ Converts study to data frame """
    data = []
    for t in study.get_trials():

        if t.state != TrialState.COMPLETE:
            continue

        trial = {"number": t.number}
        trial.update(t.user_attrs)
        trial.update(t.params)

        if "error_critical" in trial:
            trial["error_hospital"] = trial["error_critical"] + trial["error_sick"]
            trial["error_total"] = 2 * trial["error_cases"] + trial["error_hospital"]

        if hasattr(t, "value"):
            trial["value"] = t.value

        data.append(trial)

    # trial data frame
    return pd.DataFrame(data)


def get_pareto_front(study, hover=None, color=None):
    """ Plots the pareto-front of a study """

    front = []

    tf = to_df(study)

    for t in study.get_pareto_front_trials():
        front.append(t.number)

    print("Best avg.")
    print(tf.loc[tf.error_total.idxmin()])

    if hover is None:
        hover = []

    # pareto front
    pf = tf[tf.number.isin(front)]
    fig = px.scatter(pf, x="error_cases", y="error_hospital", hover_data=["number"] + hover, color=color,
                     title="Pareto Front")

    return tf, fig


def get_contour_plot(study, x_param, y_param):
    trials = [t for t in study.trials if t.state == TrialState.COMPLETE]

    x_indices = sorted(list({t.params[x_param] for t in trials if x_param in t.params}))
    y_indices = sorted(list({t.params[y_param] for t in trials if y_param in t.params}))

    z = [[float("nan") for _ in range(len(x_indices))] for _ in range(len(y_indices))]

    x_values = []
    y_values = []
    for trial in trials:
        if x_param not in trial.params or y_param not in trial.params:
            continue

        if trial.state != TrialState.COMPLETE:
            continue

        value = trial.user_attrs["error_cases"]
        if value > 2:
            continue

        x_values.append(trial.params[x_param])
        y_values.append(trial.params[y_param])
        x_i = x_indices.index(trial.params[x_param])
        y_i = y_indices.index(trial.params[y_param])

        z[y_i][x_i] = value

    colorscale = plotly.colors.PLOTLY_SCALES["Blues"]
    colorscale = [[1 - t[0], t[1]] for t in colorscale]
    colorscale.reverse()

    contour = go.Contour(
        x=x_indices,
        y=y_indices,
        z=z,
        colorbar={"title": "Objective Value"},
        colorscale=colorscale,
        connectgaps=True,
        contours_coloring="heatmap",
        hoverinfo="none",
        line_smoothing=1.3,
    )

    scatter = go.Scatter(
        x=x_values, y=y_values, marker={"color": "black"}, mode="markers", showlegend=False
    )

    layout = go.Layout(title="Contour Plot", )

    return go.Figure(data=[contour, scatter], layout=layout)


# This function was taken from optuna and adapted for multi-objective run
def get_parallel_coordinate_plot(study, params=None):
    layout = go.Layout(title="Parallel Coordinate Plot", )

    trials = []

    # Retrofittet error metric
    for t in study.trials:
        if t.state != TrialState.COMPLETE:
            continue

        value = t.user_attrs["error_cases"]
        if value <= 0.1:
            trials.append(t)

    # trials = [t for trial in trials if trial.state == TrialState.COMPLETE]

    if len(trials) == 0:
        logger.warning("Your study does not have any completed trials.")
        return go.Figure(data=[], layout=layout)

    all_params = {p_name for t in trials for p_name in t.params.keys()}
    if params is not None:
        for input_p_name in params:
            if input_p_name not in all_params:
                raise ValueError("Parameter {} does not exist in your study.".format(input_p_name))
        all_params = set(params)
    sorted_params = sorted(list(all_params))

    dims = [
        {
            "label": attr,
            "values": tuple([t.user_attrs[attr] for t in trials]),
            "range": (min([t.user_attrs[attr] for t in trials]), max([t.user_attrs[attr] for t in trials])),
        } for attr in ("error_cases", "error_critical", "error_sick")
    ]  # type: List[Dict[str, Any]]
    for p_name in sorted_params:
        values = []
        for t in trials:
            if p_name in t.params:
                values.append(t.params[p_name])
        is_categorical = False
        try:
            tuple(map(float, values))
        except (TypeError, ValueError):
            vocab = defaultdict(lambda: len(vocab))  # type: DefaultDict[str, int]
            values = [vocab[v] for v in values]
            is_categorical = True
        dim = {
            "label": p_name if len(p_name) < 20 else "{}...".format(p_name[:17]),
            "values": tuple(values),
            "range": (min(values), max(values)),
        }
        if is_categorical:
            dim["tickvals"] = list(range(len(vocab)))
            dim["ticktext"] = list(sorted(vocab.items(), key=lambda x: x[1]))
        dims.append(dim)

    traces = [
        go.Parcoords(
            dimensions=dims,
            labelangle=30,
            labelside="bottom",
            line={
                "color": dims[0]["values"],
                "colorscale": "blues",
                "colorbar": {"title": "Objective Value"},
                "showscale": True,
                "reversescale": True,
            },
        )
    ]

    figure = go.Figure(data=traces, layout=layout)

    return figure
