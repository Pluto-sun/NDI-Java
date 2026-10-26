"""仅验证历史日期映射，不连接数据库。"""
import unittest
import uuid
from datetime import datetime, timezone, timedelta
import pymysql
from unittest.mock import MagicMock, patch
from rehearse_legacy_projects import legacy_opportunity_date, insert_legacy_project, backfill_legacy_opportunity_dates, insert_legacy_responsible_people


class LegacyOpportunityDateTest(unittest.TestCase):
    def test_missing_date_is_not_replaced_with_creation_time(self):
        with self.assertRaises(ValueError):
            legacy_opportunity_date({"created_at": datetime(2020, 1, 1)})

    def test_timezone_is_normalized_without_changing_the_instant(self):
        value = datetime(2026, 7, 1, 0, 0, tzinfo=timezone(timedelta(hours=8)))
        self.assertEqual(legacy_opportunity_date({"opportunity_date": value}), datetime(2026, 6, 30, 16, 0))

    def test_sql_binding_preserves_old_date_separately_from_created_and_updated(self):
        old = dict(project_code="LEGACY-1", province_id=str(uuid.uuid4()), city_id=str(uuid.uuid4()),
                   project_name="历史项目", customer_name="客户", customer_requirement="需求",
                   total_amount=100, contract_no=None, signed_amount=None, current_stage_code="BIDDING",
                   created_at=datetime(2020, 1, 1), updated_at=datetime(2026, 10, 1),
                   opportunity_date=datetime(2026, 6, 30, 16, 0))
        target = MagicMock()
        insert_legacy_project(target, uuid.uuid4().bytes, old, "C25", str(uuid.uuid4()), "经理", uuid.uuid4().bytes)
        sql, values = target.cursor.return_value.__enter__.return_value.execute.call_args.args
        self.assertIn("updated_by,updated_at,opportunity_date", sql)
        self.assertEqual(sql.count("%s"), len(values))
        self.assertEqual(values[-1], old["opportunity_date"])
        self.assertEqual(values[-2], old["updated_at"])
        self.assertEqual(values[-4], old["created_at"])

    def test_backfill_only_restores_null_values_and_records_audit(self):
        target = MagicMock()
        project_id = uuid.uuid4().bytes
        date = datetime(2026, 6, 30, 16, 0)
        rows = [[dict(id=project_id, opportunity_date=None)], [dict(id=uuid.uuid4().bytes, opportunity_date=date)]]
        with patch('rehearse_legacy_projects.fetch_all', side_effect=rows), patch('rehearse_legacy_projects.execute') as execute:
            report = backfill_legacy_opportunity_dates(target, [dict(id='old-1', opportunity_date=date), dict(id='old-2', opportunity_date=None)])
            self.assertEqual(len(report['restored']), 1)
            self.assertEqual(report['skipped_existing'], ['old-2'])
            self.assertEqual(execute.call_count, 2)
            self.assertEqual(execute.call_args_list[0].args[2], (date, project_id))
            self.assertIn('PROJECT_LEGACY_DATE_RESTORED', execute.call_args_list[1].args[1])

    def test_missing_source_date_is_reported_without_guessing(self):
        with patch('rehearse_legacy_projects.fetch_all', return_value=[dict(id=uuid.uuid4().bytes, opportunity_date=None)]), patch('rehearse_legacy_projects.execute') as execute:
            target = MagicMock()
            report = backfill_legacy_opportunity_dates(target, [dict(id='missing', opportunity_date=None)])
            self.assertEqual(report['failed'], [dict(source_id='missing', reason='ValueError')])
            execute.assert_not_called()
            target.rollback.assert_called_once()

    def test_commit_failure_is_not_reported_as_restored(self):
        with patch('rehearse_legacy_projects.fetch_all', return_value=[dict(id=uuid.uuid4().bytes, opportunity_date=None)]), patch('rehearse_legacy_projects.execute'):
            target = MagicMock()
            target.commit.side_effect = pymysql.OperationalError('测试提交失败')
            report = backfill_legacy_opportunity_dates(target, [dict(id='commit', opportunity_date=datetime(2026, 1, 1))])
            self.assertEqual(report['restored'], [])
            self.assertEqual(len(report['failed']), 1)
            target.rollback.assert_called_once()


class LegacyResponsiblePeopleTest(unittest.TestCase):
    def test_homonyms_keep_separate_roles_and_only_one_province_binding(self):
        leader = str(uuid.uuid4())
        members = [dict(person_name_snapshot="同名", role_code=role) for role in
                   ["PROVINCE_LEADER", "CITY_ACCOUNT_MANAGER", "PRODUCT_SUPPORT_EXPERT", "PROVINCE_LEADER"]]
        with patch('rehearse_legacy_projects.execute') as execute:
            insert_legacy_responsible_people(MagicMock(), uuid.uuid4().bytes, members, leader)
            rows = [call.args[2] for call in execute.call_args_list]
            self.assertEqual(len(rows), 4)
            self.assertEqual([row[3] for row in rows], ["PROVINCE_LEADER", "CITY_MANAGER", "SUPPORT_EXPERT", None])
            self.assertEqual([row[4] for row in rows], [uuid.UUID(leader).bytes, None, None, None])
            self.assertEqual([row[5] for row in rows], [0, 1, 2, 3])

    def test_missing_leader_snapshot_uses_bound_account_name(self):
        leader = str(uuid.uuid4())
        with patch('rehearse_legacy_projects.execute') as execute:
            insert_legacy_responsible_people(MagicMock(), uuid.uuid4().bytes, [], leader)
            self.assertIn("FROM identity_user_account", execute.call_args.args[1])
            self.assertEqual(execute.call_args.args[2][-1], uuid.UUID(leader).bytes)


if __name__ == '__main__':
    unittest.main()
