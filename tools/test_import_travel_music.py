import unittest

from tools.import_travel_music import console_report


class ConsoleReportTest(unittest.TestCase):
    def test_report_is_ascii_safe_for_non_ascii_filenames(self) -> None:
        report = console_report(
            [{"event": "tnc:music.travel.track_001", "source": "旅人・测试.mp3", "bytes": 42}]
        )

        report.encode("ascii")
        self.assertIn('"count": 1', report)
        self.assertIn("\\u65c5", report)


if __name__ == "__main__":
    unittest.main()
