import math
import unittest

from unitchain import Screen, cm360, degrees_per_count


class UnitChainTest(unittest.TestCase):
    def test_degrees_per_count(self):
        self.assertAlmostEqual(degrees_per_count(0.335), 0.02345, places=9)

    def test_cm360_matches_kotlin_example(self):
        # SPEC: 800 DPI, sens 0.249 -> about 65.6 cm/360
        self.assertAlmostEqual(cm360(800, 0.249), 65.6, delta=0.1)

    def test_screen_edges_are_half_the_hfov(self):
        s = Screen(1920, 1080)
        self.assertAlmostEqual(s.yaw_of(0), -51.5, places=9)
        self.assertAlmostEqual(s.yaw_of(1920), 51.5, places=9)
        self.assertAlmostEqual(s.yaw_of(960), 0.0, places=9)

    def test_projection_is_rectilinear(self):
        s = Screen(1920, 1080)
        quarter = math.degrees(math.atan(math.tan(math.radians(51.5)) / 2))
        self.assertAlmostEqual(s.x_of(quarter), 1920 * 0.75, places=6)
        self.assertAlmostEqual(s.yaw_of(s.x_of(20.0)), 20.0, places=9)

    def test_pitch_sign(self):
        self.assertGreater(Screen(1920, 1080).pitch_of(100), 0)


if __name__ == "__main__":
    unittest.main()
