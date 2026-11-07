import unittest
from run import evaluate


class MotionCurves(unittest.TestCase):
    def test_linear_curve(self):
        self.assertEqual(evaluate([0, 2, 0, 2, 6], 1), 4)

    def test_bezier_inverts_time_instead_of_using_linear_fraction(self):
        # x(t)=t^3, y(t)=3t: x=1/8 occurs at t=1/2, hence y=1.5.
        self.assertAlmostEqual(evaluate([0, 0, 1, 0, 1, 0, 2, 1, 3], 0.125), 1.5)

    def test_stepped_holds_previous_value(self):
        self.assertEqual(evaluate([0, 2, 2, 1, 6], 0.5), 2)

    def test_inverse_step_uses_next_value(self):
        self.assertEqual(evaluate([0, 2, 3, 1, 6], 0.5), 6)

    def test_bezier_start_is_exact_zero(self):
        self.assertEqual(evaluate([0, 0, 1, 0.2, 0, 0.8, 1, 1, 1], 0), 0)

    def test_unknown_segment_is_rejected(self):
        with self.assertRaises(ValueError):
            evaluate([0, 0, 9, 1, 1], 0.5)


if __name__ == "__main__":
    unittest.main()
