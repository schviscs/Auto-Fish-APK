import pathlib
import sys
import unittest

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app" / "src" / "main" / "python"))

from vision_engine import analyze_rgba


class VisionEngineTest(unittest.TestCase):
    def test_detects_bite_signal_from_cyan_roi(self):
        width, height = 80, 60
        frame = np.zeros((height, width, 4), dtype=np.uint8)
        frame[:, :, 3] = 255
        frame[30:42, 35:47, :3] = [80, 180, 255]

        result = analyze_rgba(
            frame.tobytes(),
            width,
            height,
            {"bite_threshold": 0.01, "color_tolerance": 5},
        )

        self.assertEqual("BITE_DETECTED", result["state"])
        self.assertEqual("tap", result["action"]["type"])
        self.assertGreater(result["confidence"], 0.9)

    def test_waits_when_no_signal_is_present(self):
        width, height = 80, 60
        frame = np.zeros((height, width, 4), dtype=np.uint8)
        frame[:, :, 3] = 255

        result = analyze_rgba(frame.tobytes(), width, height, {"bite_threshold": 0.01})

        self.assertEqual("WAITING_BITE", result["state"])
        self.assertEqual("wait", result["action"]["type"])


if __name__ == "__main__":
    unittest.main()
