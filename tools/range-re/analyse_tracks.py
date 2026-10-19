"""Turn the per-frame detections of clip 2 into bot tracks and summary statistics for
out/range-spec.json. Tracks are greedy nearest-centroid matches with a short gap tolerance.
Every number carries source, n, spread and verified (false until checked by hand)."""
import json
import sys

import numpy as np

from detect_bots import run
from unitchain import Screen

MAX_JUMP = 120   # px between consecutive detections of one bot
MAX_GAP = 8      # frames a bot may vanish (outline flicker, occlusion)
MIN_TRACK = 12   # frames


def best(dets):
    """The humanoid detection of a frame: tall, narrow outline; ignore specks."""
    c = [d for d in dets if d[3] >= 1.2 * d[2] and d[3] >= 14]
    return max(c, key=lambda d: d[4]) if c else None


def tracks_of(frames):
    tracks, live = [], None
    for i, f in enumerate(frames):
        d = best(f["dets"])
        if d is None:
            if live and i - live["last"] > MAX_GAP:
                tracks.append(live); live = None
            continue
        cx, cy = d[0] + d[2] / 2, d[1] + d[3] / 2
        if live and i - live["last"] <= MAX_GAP and np.hypot(cx - live["pts"][-1][1], cy - live["pts"][-1][2]) <= MAX_JUMP:
            live["pts"].append((i, cx, cy, d[2], d[3])); live["last"] = i
        else:
            if live:
                tracks.append(live)
            live = {"pts": [(i, cx, cy, d[2], d[3])], "last": i}
    if live:
        tracks.append(live)
    return [t for t in tracks if len(t["pts"]) >= MIN_TRACK]


def stat(name, values, source, unit):
    v = np.asarray(values, float)
    return {"value": float(np.median(v)), "unit": unit, "n": int(len(v)),
            "p25": float(np.percentile(v, 25)), "p75": float(np.percentile(v, 75)),
            "min": float(v.min()), "max": float(v.max()), "source": source, "verified": False}


def main(path: str) -> None:
    d = run(path)
    fps, (w, h) = d["fps"], d["size"]
    screen = Screen(w, h)
    frames = d["frames"]
    tr = tracks_of(frames)
    src = f"{path} (hash {d['hash']})"
    life, height_deg, bearing, world_speed, first_gap = [], [], [], [], []
    last_end = None
    for t in tr:
        p = t["pts"]
        i0, i1 = p[0][0], p[-1][0]
        life.append((i1 - i0 + 1) / fps)
        top, bot = p[len(p) // 2][2] - p[len(p) // 2][4] / 2, p[len(p) // 2][2] + p[len(p) // 2][4] / 2
        height_deg.append(abs(screen.pitch_of(top) - screen.pitch_of(bot)))
        bearing.append(screen.yaw_of(p[0][1]) + frames[i0]["yaw"])
        ys = np.array([screen.yaw_of(q[1]) + frames[q[0]]["yaw"] for q in p])
        ts = np.array([q[0] / fps for q in p])
        if len(p) >= 20 and ts[-1] > ts[0]:
            world_speed.append(abs(np.polyfit(ts, ys, 1)[0]))
        if last_end is not None:
            first_gap.append((i0 - last_end) / fps)
        last_end = i1
    spec = {
        "mode": "Eliminate 100, Bot Armor on, Infinite Ammo on",
        "tracks_found": len(tr),
        "duration_s": round(frames.__len__() / fps, 1),
        "bot_visible_time_s": stat("life", life, src, "s"),
        "gap_between_bots_s": stat("gap", first_gap, src, "s") if first_gap else None,
        "bot_height_deg": stat("h", height_deg, src, "deg (outline box, noisy for far bots)"),
        "bearing_world_deg": stat("b", bearing, src, "deg (integrated view yaw, relative to clip start)"),
        "bot_world_yaw_speed_deg_s": stat("v", world_speed, src, "deg/s (strafe plus view drift)") if world_speed else None,
        "limits": "30 fps; one clip; view yaw is integrated image shift and drifts; heights are outline boxes",
    }
    with open("out/range-spec.json", "w") as f:
        json.dump(spec, f, indent=2)
    print(json.dumps({k: (v if not isinstance(v, dict) else {a: v[a] for a in ("value", "p25", "p75", "n")}) for k, v in spec.items()}, indent=1))


if __name__ == "__main__":
    main(sys.argv[1])
