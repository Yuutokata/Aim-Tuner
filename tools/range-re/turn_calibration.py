"""Clip 1 (slow turn): integrate the horizontal image shift between frames (phase correlation on
the sky/wall band, away from HUD and weapon) into a total yaw, using the unit chain. If the
player turned a known angle (a full 360 returns to the start view), the total tells whether the
FOV/width assumption is right. Writes out/turn.json (summary only)."""
import json
import sys

import cv2
import numpy as np

from unitchain import Screen


def main(path: str) -> None:
    cap = cv2.VideoCapture(path)
    w, h = int(cap.get(3)), int(cap.get(4))
    fps = cap.get(5)
    screen = Screen(w, h)
    band = (int(h * 0.08), int(h * 0.42), int(w * 0.30), int(w * 0.70))  # y0,y1,x0,x1
    win = None
    prev = None
    yaw = 0.0
    track = []
    i = 0
    while True:
        ok, im = cap.read()
        if not ok:
            break
        g = cv2.cvtColor(im[band[0]:band[1], band[2]:band[3]], cv2.COLOR_BGR2GRAY).astype(np.float32)
        if win is None:
            win = cv2.createHanningWindow((g.shape[1], g.shape[0]), cv2.CV_32F)
        if prev is not None:
            (dx, _), resp = cv2.phaseCorrelate(prev, g, win)
            # scene moves left when the view turns right: view yaw change = -dx / f
            if resp > 0.05:
                yaw += -np.degrees(np.arctan(dx / screen.focal))
        track.append(yaw)
        prev = g
        i += 1
    t = np.array(track)
    rate = np.abs(np.diff(t)) * fps
    out = {
        "source": path, "frames": i, "fps": fps, "size": [w, h],
        "total_yaw_deg": float(t[-1]), "max_abs_yaw_deg": float(np.abs(t).max()),
        "median_rate_deg_s": float(np.median(rate[rate > 1])) if (rate > 1).any() else 0.0,
        "peak_rate_deg_s": float(rate.max()), "verified": False,
        "note": "integrated image shift; drifts when shift is large or the band is featureless",
    }
    with open("out/turn.json", "w") as f:
        json.dump(out, f, indent=2)
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    main(sys.argv[1])
