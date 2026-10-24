"""Idle / run clips: bot tracks (bot_tracks.py) -> lifetime, spawn gaps, bearing, angular height,
strafe speed and segments. Usage: python analyse_clips.py <clip.mp4> ; prints JSON, caches nothing
beyond detect_bots' cache. Heights and distances are rough: the outline box covers head+torso only."""
import json
import sys

import numpy as np

import bot_tracks as B
from detect_bots import run
from unitchain import Screen

BOT_HEIGHT_M = 1.8      # ASSUMPTION, not measured
MOVE_DEG_S = 4.0        # below this a strafing bot counts as stopped
CAM_QUIET_DEG_S = 3.0   # use strafe samples only while the view is (nearly) still


def q(v, name):
    v = np.asarray(v, float)
    if len(v) == 0:
        return {"n": 0}
    return {"median": round(float(np.median(v)), 3), "p25": round(float(np.percentile(v, 25)), 3),
            "p75": round(float(np.percentile(v, 75)), 3), "min": round(float(v.min()), 3),
            "max": round(float(v.max()), 3), "n": int(len(v))}


def smooth(a, k=5):
    p = np.pad(a, k // 2, mode="edge")
    return np.array([np.median(p[i:i + k]) for i in range(len(a))])


def strafe(track, frames, fps, screen):
    p = track["pts"]
    idx = np.array([x[0] for x in p])
    world = np.array([screen.yaw_of(x[1]) + frames[x[0]]["yaw"] for x in p])
    view = np.array([frames[i]["yaw"] for i in idx])
    t = idx / fps
    w = 6
    out = []
    for i in range(w, len(p)):
        dt = t[i] - t[i - w]
        if dt <= 0 or dt > 0.3:
            continue
        if abs(view[i] - view[i - w]) / dt > CAM_QUIET_DEG_S:
            continue
        out.append((t[i], (world[i] - world[i - w]) / dt))
    return out


def segments(vs):
    """Split (t, v) samples into moving-left / moving-right / stopped runs; durations in s."""
    segs, cur, start, last = [], None, None, None
    for t, v in vs:
        s = 0 if abs(v) < MOVE_DEG_S else (1 if v > 0 else -1)
        if cur is None or s != cur or t - last > 0.2:
            if cur is not None and last - start > 0.05:
                segs.append((cur, last - start))
            cur, start = s, t
        last = t
    if cur is not None and last - start > 0.05:
        segs.append((cur, last - start))
    return segs


def main(path, t0=0.0, t1=1e9):
    d = run(path)
    fps, (w, h) = d["fps"], d["size"]
    screen = Screen(w, h)
    frames = d["frames"]
    n0, n1 = int(t0 * fps), min(len(frames), int(t1 * fps))
    boxes = [B.merge_fragments(f["dets"]) if n0 <= i < n1 else [] for i, f in enumerate(frames)]
    tracks = B.track(boxes)
    res = {"clip": path, "window_s": [t0, min(t1, len(frames) / fps)], "fps": fps, "tracks": len(tracks),
           "max_simultaneous": max(len(b) for b in boxes)}
    life = [(t["pts"][-1][0] - t["pts"][0][0] + 1) / fps for t in tracks]
    starts = sorted(t["pts"][0][0] / fps for t in tracks)
    res["lifetime_s"] = q(life, "life")
    res["spawn_gap_s"] = q(np.diff(starts), "gap")
    bearing, feet_pitch, hdeg, dist = [], [], [], []
    for t in tracks:
        p = t["pts"]
        i0 = p[0][0]
        bearing.append(screen.yaw_of(p[0][1]) + frames[i0]["yaw"])
        hb = np.percentile([x[4] for x in p], 90)
        feet_pitch.append(screen.pitch_of(p[0][2] + hb * B.FULL_OVER_BOX))
        ang = np.degrees(np.arctan(hb * B.FULL_OVER_BOX / screen.focal))
        hdeg.append(ang)
        dist.append(BOT_HEIGHT_M / np.tan(np.radians(ang)))
    res["first_seen_bearing_deg"] = q(bearing, "b")
    res["first_seen_feet_pitch_deg"] = q(feet_pitch, "e")
    res["bot_height_deg_p90_box_x2.8"] = q(hdeg, "h")
    res["implied_distance_m_assuming_1.8m"] = q(dist, "d")
    vs_all, segs_all, speeds, lin = [], [], [], []
    for t, dm in zip(tracks, dist):
        vs = strafe(t, frames, fps, screen)
        if len(vs) < 30:
            continue
        vs_all += vs
        segs_all += segments(vs)
        speeds += [abs(v) for _, v in vs if abs(v) >= MOVE_DEG_S]
        lin += [abs(v) * np.pi / 180 * dm for _, v in vs if abs(v) >= MOVE_DEG_S]
    if vs_all:
        mv = [s for s in segs_all if s[0] != 0]
        res["strafe"] = {
            "moving_speed_deg_s": q(speeds, "v"), "moving_speed_m_s_at_implied_dist": q(lin, "m"),
            "moving_segment_s": q([s[1] for s in mv], "m"),
            "stopped_segment_s": q([s[1] for s in segs_all if s[0] == 0], "s"),
            "share_time_stopped": round(sum(s[1] for s in segs_all if s[0] == 0) / max(1e-9, sum(s[1] for s in segs_all)), 3),
            "samples": len(vs_all)}
    res["track_list"] = [[round(t["pts"][0][0] / fps, 2), round(t["pts"][-1][0] / fps, 2), len(t["pts"])] for t in tracks]
    print(json.dumps(res, indent=1))
    return res


if __name__ == "__main__":
    a = sys.argv
    main(a[1], float(a[2]) if len(a) > 2 else 0.0, float(a[3]) if len(a) > 3 else 1e9)
