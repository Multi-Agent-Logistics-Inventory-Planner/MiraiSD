"""Safety boundary tests; PostgreSQL integration is run separately on the dev container."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('setup_dev_db', Path(__file__).resolve().parents[1] / 'setup_dev_db.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


def result(value):
    return subprocess.CompletedProcess([], 0, json.dumps(value), '')


def container():
    return {'Id': 'pinned-container-id', 'Name': '/postgres-dev', 'State': {'Running': True},
            'Config': {'Labels': {'com.docker.compose.service': 'postgres-dev',
                                  'com.docker.compose.project': 'infra'},
                       'Env': ['POSTGRES_DB=mirai_inventory', 'POSTGRES_USER=postgres']}}


class TargetSafetyTest(unittest.TestCase):
    @patch.dict('os.environ', {}, clear=True)
    @patch.object(setup, 'run')
    def test_remote_context_rejected_before_inspecting_or_executing(self, run):
        run.return_value = result([{'Endpoints': {'docker': {'Host': 'ssh://production'}}}])
        with self.assertRaisesRegex(RuntimeError, 'non-local'):
            setup.docker_target()
        self.assertEqual(run.call_count, 1)

    @patch.dict('os.environ', {'DOCKER_HOST': 'tcp://remote:2375'}, clear=True)
    @patch.object(setup, 'run')
    def test_remote_host_override_rejected_without_docker_calls(self, run):
        with self.assertRaisesRegex(RuntimeError, 'non-local'):
            setup.docker_target()
        run.assert_not_called()

    @patch.dict('os.environ', {'DOCKER_HOST': 'unix:///local.sock', 'PGHOST': 'production'}, clear=True)
    @patch.object(setup, 'run')
    def test_pins_container_and_socket_and_clears_postgres_environment(self, run):
        run.return_value = result([container()])
        command = setup.docker_target()
        self.assertEqual(command[:3], ['docker', '--host', 'unix:///local.sock'])
        self.assertIn('pinned-container-id', command)
        self.assertIn('/var/run/postgresql', command)
        self.assertEqual(command[6:8], ['env', '-i'])
        self.assertIn('-X', command)
        self.assertNotIn('production', command)

    @patch.dict('os.environ', {'DOCKER_HOST': 'unix:///local.sock'}, clear=True)
    @patch.object(setup, 'run')
    def test_wrong_service_or_database_rejected(self, run):
        for field in ('service', 'database', 'running'):
            info = container()
            if field == 'service':
                info['Config']['Labels']['com.docker.compose.service'] = 'postgres-prod'
            elif field == 'database':
                info['Config']['Env'] = ['POSTGRES_DB=production', 'POSTGRES_USER=postgres']
            else:
                info['State']['Running'] = False
            run.return_value = result([info])
            with self.assertRaisesRegex(RuntimeError, 'Refusing target'):
                setup.docker_target()


if __name__ == '__main__':
    unittest.main()
