"""Pixel <-> angle conversion for the range recordings. Mirrors the Kotlin unit chain
(UnitChainTest): Valorant yaw 0.07 deg/count at sensitivity 1, horizontal FOV 103 deg at 16:9,
rectilinear projection, crosshair at the screen centre."""
import math
from dataclasses import dataclass

VALORANT_YAW = 0.07
VALORANT_HFOV = 103.0


def degrees_per_count(sensitivity: float, yaw: float = VALORANT_YAW) -> float:
    return sensitivity * yaw


def cm360(dpi: float, sensitivity: float, yaw: float = VALORANT_YAW) -> float:
    return 360.0 * 2.54 / (dpi * sensitivity * yaw)


@dataclass(frozen=True)
class Screen:
    width: int
    height: int
    hfov: float = VALORANT_HFOV

    @property
    def focal(self) -> float:
        """Focal length in pixels."""
        return (self.width / 2.0) / math.tan(math.radians(self.hfov) / 2.0)

    def yaw_of(self, x: float) -> float:
        return math.degrees(math.atan((x - self.width / 2.0) / self.focal))

    def pitch_of(self, y: float) -> float:
        """Positive is up. Uses the same focal length for both axes (square pixels)."""
        return math.degrees(math.atan((self.height / 2.0 - y) / self.focal))

    def x_of(self, yaw: float) -> float:
        return self.width / 2.0 + self.focal * math.tan(math.radians(yaw))

    def angular_size(self, pixels: float, at: float) -> float:
        """Angle covered by `pixels` (horizontal) starting at screen position `at`."""
        return self.yaw_of(at + pixels) - self.yaw_of(at)
