#!/usr/bin/env python3
# Usage inside `nix develop`: python tools/convert_recordings.py [recordings/ses-001] [app/src/main/assets/replay]

import io
import re
import shutil
import sqlite3
import sys
import tempfile
from pathlib import Path

import numpy as np

EXPECTED_CHANNELS = [
    "O1", "O2", "Fp2", "Fp1",
    "contact_O1", "contact_O2", "contact_Fp2", "contact_Fp1",
    "Accel_x", "Accel_y", "Accel_z",
    "Gyro_x", "Gyro_y", "Gyro_z",
    "Sample", "Streaming", "Battery",
]
FORMATS = ["%.3f"] * 4 + ["%d"] * 4 + ["%.4f"] * 3 + ["%.3f"] * 3 + ["%d", "%d", "%.1f"]
NAME_RE = re.compile(r"_task-(?P<task>[^_]*)_run-(?P<run>\d+)_(?P<date>\d{8})_(?P<time>\d{6})_eeg\.db$")


def read_recording(db_path: Path) -> np.ndarray:
    # Opening a WAL database checkpoints and rewrites it, so only ever open a private copy.
    with tempfile.TemporaryDirectory() as tmp:
        copy = Path(tmp) / db_path.name
        for suffix in ("", "-wal", "-shm"):
            src = Path(str(db_path) + suffix)
            if src.exists():
                shutil.copy2(src, str(copy) + suffix)
        con = sqlite3.connect(f"file:{copy}?mode=ro", uri=True)
        try:
            tables = [r[0] for r in con.execute("select name from sqlite_master where type='table'")]
            data_table = next(t for t in tables if t.startswith("data_"))
            meta_table = next(t for t in tables if t.startswith("meta_"))
            channels = con.execute(f"select channels, sf from `{meta_table}`").fetchone()
            names = [c.strip() for c in str(channels[0]).strip("'").split(",")]
            if names != EXPECTED_CHANNELS:
                raise ValueError(f"{db_path.name}: unexpected channel order {names}")
            if float(channels[1]) != 250.0:
                raise ValueError(f"{db_path.name}: sample rate {channels[1]} is not 250 Hz")
            rows = con.execute(f"select data from `{data_table}` order by rowid").fetchall()
        finally:
            con.close()
    blocks = [np.load(io.BytesIO(r[0]), allow_pickle=False) for r in rows]
    data = np.concatenate(blocks, axis=1).T
    order = np.argsort(data[:, 14], kind="stable")
    return data[order]


def main() -> None:
    src_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "recordings/ses-001")
    out_dir = Path(sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/replay")
    out_dir.mkdir(parents=True, exist_ok=True)
    used = set()
    for db_path in sorted(src_dir.glob("*_eeg.db")):
        m = NAME_RE.search(db_path.name)
        task = m.group("task") if m and m.group("task") else f"untitled-{m.group('time') if m else db_path.stem}"
        name = task if task not in used else f"{task}_run-{m.group('run')}_{m.group('time')}"
        used.add(name)
        data = read_recording(db_path)
        gaps = int((np.diff(data[:, 14]) != 1).sum())
        dummies = int((data[:, 15] == 0).sum())
        out = out_dir / f"{name}.csv"
        np.savetxt(out, data, fmt=FORMATS, delimiter=",", header=",".join(EXPECTED_CHANNELS), comments="")
        print(f"{out}: {len(data)} samples ({len(data) / 250:.1f} s), sample gaps {gaps}, gap-filled {dummies}")


if __name__ == "__main__":
    main()
