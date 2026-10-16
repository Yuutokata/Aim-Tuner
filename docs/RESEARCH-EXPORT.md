# Research export

History, then "Share data for research" writes all stored rounds into one file,
`aimtune-research-<yyyyMMdd-HHmmss>.json.gz`, in `%APPDATA%\AimTune\exports\research\`. AimTune never
uploads it; the person sends it themselves. It exists because the method has never been run on other
people's recordings (docs/ASSESSMENT.md D4): the files let someone check the first-reach definition, the
low-speed pattern of the acceleration analysis, and whether the score curve has an interior optimum for people.

Size: about 70 KB compressed per 45-second round at a 1 kHz mouse (28 rounds: 2 MB).

## Contents

```
{ "format": "aimtune-research", "formatVersion": 1, "appVersion": "0.1.0", "metricsVersion": 3, "notes": "...",
  "sessions": [ { "index": 0, "kind": "sensitivity" | "accel_ab", "gameId": "valorant", "dpi": 800.0, "dayIndex": 0,
      "rounds": [ { "scenario": "flick", "seed": 42, "cm360": 40.0, "dpi": 800.0, "blind": true, "accel": null,
          "startOffsetSeconds": 5.0, "durationNanos": 45000000000,
          "mouse": { "dtNanos": [...], "dx": [...], "dy": [...] },
          "targetEvents": [ {"type":"spawn","tNanos":0,"id":0,"x":5.0,"y":1.0,"z":-9.0,"radius":0.25},
                            {"type":"waypoint","tNanos":..,"id":..,"x":..,"y":..,"z":..},
                            {"type":"miss","tNanos":..}, {"type":"hit","tNanos":..,"id":..} ],
          "metrics": { "accuracy": ..., ... } } ] } ] }
```

* Times are nanoseconds from the start of the round. `dtNanos` is the time since the previous mouse report
  (the first since the round start); `dx`, `dy` are raw counts. Degrees per count follow from `cm360` and `dpi`:
  `360 / (cm360 / 2.54 * dpi)`.
* Target positions are metres in the camera frame at the start of the round (x right, y up, -z forward). The view
  starts at yaw 0, pitch 0 and moves by the mouse counts; a shot hits if the ray along the view meets the sphere.
* `accel` is not null when a simulated Raw Accel curve was applied to the counts in the trainer.
* `kind` tells a sensitivity test from an acceleration calibration/A-B run; `blind` and `accel` tell plain from curve rounds.

## What it does not contain

No name, computer or user name, file path, clock time or date (sessions get a `dayIndex` counted from the
first session, rounds a `startOffsetSeconds` within their session), and nothing from other programs.
DPI, sensitivity and the game id are included on purpose. `ResearchExport` writes as a stream and never
builds the whole file in memory.
