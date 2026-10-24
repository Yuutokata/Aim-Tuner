"""Clip 2 (Eliminate 100 run): detect the bots by their purple outline, per frame, and track the
view yaw by integrating the image shift. Detections are cached on disk keyed by the file hash.
Output: cache/<hash>-detections.json (per frame), no frames are stored."""
import hashlib
import json
import os
import sys

import cv2
import numpy as np

from unitchain import Screen

HUE = (128, 168)       # OpenCV hue of the purple outline
MIN_S, MIN_V = 90, 90
MIN_AREA = 40          # px of the closed outline mask
CACHE = "cache"


def file_hash(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()[:16]


def allowed(w: int, h: int) -> np.ndarray:
    """Pixels where a bot can appear: not the top bar, the left panel, the weapon or the bottom HUD."""
    m = np.ones((h, w), np.uint8)
    m[: int(h * 0.10), :] = 0
    m[: int(h * 0.45), : int(w * 0.16)] = 0
    m[int(h * 0.55):, int(w * 0.52):] = 0
    m[int(h * 0.80):, :] = 0
    return m


def run(path: str) -> dict:
    key = file_hash(path)
    os.makedirs(CACHE, exist_ok=True)
    cache_file = f"{CACHE}/{key}-detections.json"
    if os.path.exists(cache_file):
        with open(cache_file) as f:
            return json.load(f)
    cap = cv2.VideoCapture(path)
    w, h, fps = int(cap.get(3)), int(cap.get(4)), cap.get(5)
    screen = Screen(w, h)
    ok_mask = allowed(w, h)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
    band = (int(h * 0.08), int(h * 0.40), int(w * 0.30), int(w * 0.70))
    win, prev, yaw = None, None, 0.0
    frames = []
    while True:
        ok, im = cap.read()
        if not ok:
            break
        hsv = cv2.cvtColor(im, cv2.COLOR_BGR2HSV)
        m = ((hsv[..., 0] >= HUE[0]) & (hsv[..., 0] <= HUE[1]) & (hsv[..., 1] >= MIN_S) & (hsv[..., 2] >= MIN_V)).astype(np.uint8)
        m = cv2.morphologyEx(m * ok_mask, cv2.MORPH_CLOSE, kernel)
        n, _, stats, cent = cv2.connectedComponentsWithStats(m)
        dets = []
        for k in range(1, n):
            x, y, bw, bh, area = stats[k]
            if area >= MIN_AREA and bh >= 12:
                dets.append([int(x), int(y), int(bw), int(bh), int(area)])
        g = cv2.cvtColor(im[band[0]:band[1], band[2]:band[3]], cv2.COLOR_BGR2GRAY).astype(np.float32)
        if win is None:
            win = cv2.createHanningWindow((g.shape[1], g.shape[0]), cv2.CV_32F)
        if prev is not None:
            (dx, _), resp = cv2.phaseCorrelate(prev, g, win)
            if resp > 0.05:
                yaw += -float(np.degrees(np.arctan(dx / screen.focal)))
        prev = g
        frames.append({"yaw": yaw, "dets": dets})
    out = {"source": path, "hash": key, "fps": fps, "size": [w, h], "frames": frames}
    with open(cache_file, "w") as f:
        json.dump(out, f)
    return out


if __name__ == "__main__":
    d = run(sys.argv[1])
    counts = [len(f["dets"]) for f in d["frames"]]
    print("frames", len(counts), "with detections", sum(1 for c in counts if c), "max per frame", max(counts))
