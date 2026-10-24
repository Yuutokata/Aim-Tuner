"""Merge the purple-outline fragments of each frame into one box per bot and track the bots over
time for a still camera (idle clips, eye-height clip). Pixel coordinates are used directly.
The outline covers the head and torso only; FULL_OVER_BOX scales the box height to the whole bot
(calibrated on 07-eyeheight, see out/range-summary.md)."""
import numpy as np

MERGE_DX, MERGE_DY = 70, 90   # px: fragments closer than this belong to one bot
MAX_JUMP = 60                 # px per frame between detections of one bot (60 fps)
MAX_GAP = 12                  # frames a bot may vanish (outline flicker, occlusion)
MIN_TRACK = 6                 # frames (0.1 s at 60 fps)
MIN_BOX_H = 14
FULL_OVER_BOX = 2.8


def merge_fragments(dets):
    """Union-find over fragments that are close; returns [x0, y0, x1, y1, area] per bot."""
    dets = [d for d in dets if d[3] >= 6]
    parent = list(range(len(dets)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i, a in enumerate(dets):
        for j in range(i):
            b = dets[j]
            gx = max(a[0], b[0]) - min(a[0] + a[2], b[0] + b[2])
            gy = max(a[1], b[1]) - min(a[1] + a[3], b[1] + b[3])
            if gx < MERGE_DX and gy < MERGE_DY:
                parent[find(i)] = find(j)
    groups = {}
    for i, d in enumerate(dets):
        groups.setdefault(find(i), []).append(d)
    boxes = []
    for g in groups.values():
        x0, y0 = min(d[0] for d in g), min(d[1] for d in g)
        x1, y1 = max(d[0] + d[2] for d in g), max(d[1] + d[3] for d in g)
        if y1 - y0 >= MIN_BOX_H and sum(d[4] for d in g) >= 80:
            boxes.append([x0, y0, x1, y1, sum(d[4] for d in g)])
    return boxes


def track(frames):
    """frames: list of per-frame box lists. Returns tracks as dicts with pts [(frame, cx, top, w, h)]."""
    done, live = [], []
    for i, boxes in enumerate(frames):
        free = list(boxes)
        for t in sorted(live, key=lambda t: -len(t["pts"])):
            _, px, py, _, _ = t["pts"][-1]
            best, bd = None, MAX_JUMP * max(1, i - t["pts"][-1][0])
            for b in free:
                dist = np.hypot((b[0] + b[2]) / 2 - px, b[1] - py)
                if dist < bd:
                    best, bd = b, dist
            if best is not None:
                free.remove(best)
                t["pts"].append((i, (best[0] + best[2]) / 2, best[1], best[2] - best[0], best[3] - best[1]))
        still = []
        for t in live:
            (still if i - t["pts"][-1][0] <= MAX_GAP else done).append(t)
        live = still + [{"pts": [(i, (b[0] + b[2]) / 2, b[1], b[2] - b[0], b[3] - b[1])]} for b in free]
    done += live
    return [t for t in done if len(t["pts"]) >= MIN_TRACK]
