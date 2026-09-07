import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect-container-metrics.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class ContainerMetricsTest(unittest.TestCase):
    def test_units(self):
        self.assertEqual(collector.memory_bytes("1.5GiB"), 1.5 * 1024**3)
        self.assertEqual(collector.memory_bytes("512 MB"), 512000000)
        with self.assertRaises(ValueError):
            collector.memory_bytes("unknown")

    def test_running_and_stopped_health(self):
        states = [dict(name="/shopsphere-prod-api-1", status="running", healthy=True, restarts=2, started="2026-09-07T00:00:00.123456789Z"),
                  dict(name="/shopsphere-prod-db-1", status="exited", healthy=False, restarts=0, started="2026-09-07T00:00:00Z")]
        sample = dict(Name="shopsphere-prod-api-1", MemUsage="1MiB / 2MiB", CPUPerc="2.5%")
        with patch.object(collector, "docker", side_effect=["abcdef123456\nabcdef123457", "\n".join(map(json.dumps, states)), json.dumps(sample)]):
            output = collector.collect()
        self.assertIn('shopsphere_container_healthy{name="shopsphere-prod-db-1"} 0', output)
        self.assertIn('shopsphere_container_cpu_percent{name="shopsphere-prod-api-1"} 2.5', output)
        self.assertNotIn("password", output)

    def test_wrong_project_rejected(self):
        with patch.object(collector, "docker", return_value=""):
            with self.assertRaises(RuntimeError):
                collector.collect()

    def test_atomic_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            collector.publish(directory, "test_metric 1\n")
            self.assertEqual((Path(directory) / "containers.prom").read_text(), "test_metric 1\n")
            self.assertEqual(len(list(Path(directory).iterdir())), 1)


if __name__ == "__main__":
    unittest.main()
