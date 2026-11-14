#!/usr/bin/env python3
# Usage inside `nix develop`: python tools/gesture_ground_truth.py [app/src/main/assets/replay]
#
# Expected values for DetectorReplayTest, worked out offline without the detector: zero-phase filters,
# a centred RMS window, and gyro bias taken from the still parts of the same session.

import sys
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.signal import butter, find_peaks, sosfiltfilt

FS = 250
SESSION_1_STILL = ["szczeka-docisk", "szczeka-otwierani", "poj-mrug", "podw-mrug", "zaamkn-oczy", "untitled-125247"]


def load(replay: Path, name: str) -> pd.DataFrame:
    return pd.read_csv(replay / f"{name}.csv")


def clench_segments(d: pd.DataFrame, threshold: float = 2.5) -> list[tuple[float, float]]:
    eeg = d[["O1", "O2", "Fp2", "Fp1"]].to_numpy()
    hf = sosfiltfilt(butter(4, [30, 100], btype="band", fs=FS, output="sos"), eeg - eeg[0], axis=0)
    box = np.ones(int(0.1 * FS)) / int(0.1 * FS)
    rms = np.sqrt(np.stack([np.convolve(hf[:, c] ** 2, box, mode="same") for c in range(4)], axis=1))
    ratio = (rms / np.percentile(rms[FS:], 20, axis=0)).mean(axis=1)
    above = np.r_[False, ratio > threshold, False].astype(int)
    starts = np.flatnonzero(np.diff(above) == 1)
    ends = np.flatnonzero(np.diff(above) == -1)
    segs = []
    for s, e in zip(starts, ends):
        if segs and (s - segs[-1][1]) / FS < 0.15:
            segs[-1] = (segs[-1][0], e)
        else:
            segs.append((s, e))
    return [(s / FS, e / FS) for s, e in segs if (e - s) / FS >= 0.2]


def gyro_bias(replay: Path) -> np.ndarray:
    g = np.concatenate([load(replay, n)[["Gyro_x", "Gyro_y", "Gyro_z"]].to_numpy() for n in SESSION_1_STILL])
    return np.median(g[np.linalg.norm(g, axis=1) < 8], axis=0)


def head_reversals(d: pd.DataFrame, bias: np.ndarray, axis: int) -> list[tuple[float, float]]:
    angle = np.cumsum(d[["Gyro_x", "Gyro_y", "Gyro_z"]].to_numpy()[:, axis] - bias[axis]) / FS
    peaks, _ = find_peaks(angle, prominence=15)
    troughs, _ = find_peaks(-angle, prominence=15)
    return [(i / FS, angle[i]) for i in sorted(np.r_[peaks, troughs])]


def main() -> None:
    replay = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/replay")

    print("clench holds above 2.5x baseline (szczeka-docisk):")
    for on, off in clench_segments(load(replay, "szczeka-docisk")):
        print(f"  {on:5.2f}-{off:5.2f} s  {off - on:.2f} s")
    for name in ("szczeka-otwierani", "zaamkn-oczy"):
        print(f"clench holds in {name}: {len(clench_segments(load(replay, name)))}")

    bias = gyro_bias(replay)
    print(f"session 1 gyro bias from still samples: {np.round(bias, 2)} deg/s")
    for name, axis in (("obrot-glowa-poziomo", 1), ("obrot-glowa-pion", 2)):
        rev = head_reversals(load(replay, name), bias, axis)
        pos = sum(1 for _, a in rev if a > 0)
        print(f"{name}: {len(rev)} reversals on {'xyz'[axis]} ({pos} on +, {len(rev) - pos} on -), angle from the start pose:")
        print("  " + " ".join(f"{t:.2f}s:{a:+.0f}" for t, a in rev))


if __name__ == "__main__":
    main()
