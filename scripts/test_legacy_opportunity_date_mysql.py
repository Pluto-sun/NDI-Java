"""使用显式指定的空隔离库验证日期补回 SQL；未配置时跳过，不访问业务库。"""
import os
import unittest
import uuid
from datetime import datetime
import pymysql
from rehearse_legacy_projects import parse_url, execute, fetch_all, backfill_legacy_opportunity_dates


class LegacyOpportunityDateMySQLTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        url = os.environ.get('NDI_REHEARSAL_TEST_URL')
        if not url:
            raise unittest.SkipTest('未指定空隔离库')
        config = parse_url(url)
        if not config['database'].startswith('ndi_rehearsal_'):
            raise ValueError('测试库名称必须以 ndi_rehearsal_ 开头')
        cls.connection = pymysql.connect(**config)
        if fetch_all(cls.connection, 'SHOW TABLES'):
            cls.connection.close()
            raise ValueError('拒绝写入非空数据库')
        execute(cls.connection, 'CREATE TABLE project_project(id BINARY(16) PRIMARY KEY,opportunity_date DATETIME(6) NULL)')
        execute(cls.connection, 'CREATE TABLE project_source_record(project_id BINARY(16),source_system VARCHAR(40),source_record_id VARCHAR(100))')
        execute(cls.connection, 'CREATE TABLE system_audit_event(id BINARY(16) PRIMARY KEY,actor_id BINARY(16),event_type VARCHAR(100),resource_type VARCHAR(100),resource_id BINARY(16),detail JSON,occurred_at DATETIME(6))')
        cls.connection.commit()

    @classmethod
    def tearDownClass(cls):
        cls.connection.close()

    def project(self, value):
        project = uuid.uuid4().bytes
        source_id = str(uuid.uuid4())
        execute(self.connection, 'INSERT INTO project_project VALUES(%s,%s)', (project, value))
        execute(self.connection, "INSERT INTO project_source_record VALUES(%s,'LEGACY_MYSQL',%s)", (project, source_id))
        self.connection.commit()
        return project, source_id

    def test_restores_only_null_dates_and_is_idempotent(self):
        old = datetime(2026, 6, 30, 16, 0, 0, 123456)
        approved = datetime(2026, 7, 10)
        project, source_id = self.project(None)
        other, other_source = self.project(approved)
        rows = [dict(id=source_id, opportunity_date=old), dict(id=other_source, opportunity_date=old)]
        report = backfill_legacy_opportunity_dates(self.connection, rows)
        self.assertEqual(len(report['restored']), 1)
        self.assertEqual(report['skipped_existing'], [other_source])
        self.assertEqual(fetch_all(self.connection, 'SELECT opportunity_date FROM project_project WHERE id=%s', (project,))[0]['opportunity_date'], old)
        self.assertEqual(fetch_all(self.connection, 'SELECT opportunity_date FROM project_project WHERE id=%s', (other,))[0]['opportunity_date'], approved)
        second = backfill_legacy_opportunity_dates(self.connection, rows)
        self.assertEqual(second['restored'], [])
        self.assertEqual(fetch_all(self.connection, 'SELECT COUNT(*) AS n FROM system_audit_event WHERE resource_id=%s', (project,))[0]['n'], 1)

    def test_missing_source_date_never_becomes_a_guessed_date(self):
        project, source_id = self.project(None)
        report = backfill_legacy_opportunity_dates(self.connection, [dict(id=source_id, opportunity_date=None)])
        self.assertEqual(len(report['failed']), 1)
        self.assertIsNone(fetch_all(self.connection, 'SELECT opportunity_date FROM project_project WHERE id=%s', (project,))[0]['opportunity_date'])


if __name__ == '__main__':
    unittest.main()
