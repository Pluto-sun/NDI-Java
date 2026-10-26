#!/usr/bin/env python3
"""从旧库只读抽取项目，在已执行 Flyway 的隔离 MySQL 中演练迁移。"""

import argparse
import csv
import json
import os
import sys
import unicodedata
import uuid
from collections import Counter, defaultdict
from datetime import datetime, time, timezone
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlparse, unquote

import pymysql


AMOUNT_COLUMNS = {
    "NETWORK": "network_amount",
    "GEWU": "gewu_amount",
    "BIG_DATA": "big_data_amount",
    "WANGWU": "wangwu_amount",
}
STAGES = {"SOLUTION_EXCHANGE", "BIDDING", "SIGNED", "COLLECTION", "TERMINATED"}


def parse_url(raw):
    value = urlparse(raw)
    if value.scheme not in {"mysql", "mysql+pymysql"} or not value.hostname or not value.path:
        raise ValueError("数据库连接 URL 必须为 MySQL URL")
    return dict(host=value.hostname, port=value.port or 3306,
                user=unquote(value.username or ""), password=unquote(value.password or ""),
                database=value.path.lstrip("/"), charset="utf8mb4",
                cursorclass=pymysql.cursors.DictCursor, autocommit=False)


def source_url(env_path):
    for line in env_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("DATABASE_URL="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise ValueError("旧库 .env 缺少 DATABASE_URL")


def binary(value):
    return uuid.UUID(str(value)).bytes


def fresh_id():
    return uuid.uuid4().bytes


def fetch_all(connection, query, params=()):
    with connection.cursor() as cursor:
        cursor.execute(query, params)
        return cursor.fetchall()


def execute(connection, query, params=()):
    with connection.cursor() as cursor:
        cursor.execute(query, params)


def norm(value):
    return " ".join(unicodedata.normalize("NFKC", value.strip()).lower().split())


def as_datetime(value):
    if isinstance(value, datetime):
        return value
    return datetime.combine(value, time.min)


def legacy_opportunity_date(old):
    value = old.get("opportunity_date")
    if not isinstance(value, datetime):
        raise ValueError("旧项目缺少项目日期")
    # 旧 MySQL DATETIME 存放 UTC；存在显式时区时先统一为 UTC。
    return value.astimezone(timezone.utc).replace(tzinfo=None) if value.tzinfo else value


def insert_legacy_project(target, project_id, old, industry_code, leader_id, manager, creator_id):
    execute(target, """INSERT INTO project_project
        (id,project_code,province_id,city_id,project_name,customer_name,industry_code,
         customer_need,total_amount,contract_no,signed_amount,current_stage_code,
         province_leader_user_id,city_account_manager_name,version_no,created_by,created_at,
         updated_by,updated_at,opportunity_date)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,0,%s,%s,%s,%s,%s)""",
        (project_id, old["project_code"], binary(old["province_id"]), binary(old["city_id"]),
         old["project_name"], old["customer_name"], industry_code,
         old["customer_requirement"], old["total_amount"], old["contract_no"],
         old["signed_amount"], old["current_stage_code"], binary(leader_id), manager,
         creator_id, old["created_at"], creator_id, old["updated_at"] or old["created_at"],
         legacy_opportunity_date(old)))


def insert_legacy_responsible_people(target, project_id, members, leader_id):
    """保留旧人员职责；同名不合并，只有省分负责人绑定原账号。"""
    roles = {"PROVINCE_LEADER": "PROVINCE_LEADER",
             "CITY_ACCOUNT_MANAGER": "CITY_MANAGER",
             "PRODUCT_SUPPORT_EXPERT": "SUPPORT_EXPERT"}
    province_assigned = False
    position = 0
    for member in members:
        name = (member["person_name_snapshot"] or "").strip()
        if not name:
            continue
        role = roles.get(member["role_code"])
        account = None
        if role == "PROVINCE_LEADER":
            if province_assigned:
                role = None
            else:
                province_assigned = True
                account = binary(leader_id)
        execute(target, """INSERT INTO project_responsible_person
            (id,project_id,person_name,responsibility,user_id,sort_order)
            VALUES (%s,%s,%s,%s,%s,%s)""",
            (fresh_id(), project_id, name, role, account, position))
        position += 1
    if not province_assigned:
        execute(target, """INSERT INTO project_responsible_person
            (id,project_id,person_name,responsibility,user_id,sort_order)
            SELECT %s,%s,display_name,'PROVINCE_LEADER',id,%s
            FROM identity_user_account WHERE id=%s""",
            (fresh_id(), project_id, position, binary(leader_id)))


def backfill_legacy_opportunity_dates(target, source_projects):
    report = {"restored": [], "skipped_existing": [], "unmapped": [], "failed": []}
    for old in source_projects:
        source_id = old["id"]
        try:
            rows = fetch_all(target, """SELECT project.id,project.opportunity_date
                FROM project_project project JOIN project_source_record source ON source.project_id=project.id
                WHERE source.source_system='LEGACY_MYSQL' AND source.source_record_id=%s FOR UPDATE""", (source_id,))
            if not rows:
                category, result = "unmapped", source_id
            elif rows[0]["opportunity_date"] is not None:
                category, result = "skipped_existing", source_id
            else:
                value = legacy_opportunity_date(old)
                project_id = rows[0]["id"]
                execute(target, "UPDATE project_project SET opportunity_date=%s WHERE id=%s AND opportunity_date IS NULL",
                        (value, project_id))
                detail = json.dumps({"sourceId": source_id, "opportunityDateUtc": value.isoformat() + "Z"})
                execute(target, """INSERT INTO system_audit_event
                    (id,actor_id,event_type,resource_type,resource_id,detail,occurred_at)
                    VALUES (%s,NULL,'PROJECT_LEGACY_DATE_RESTORED','PROJECT',%s,%s,%s)""",
                        (fresh_id(), project_id, detail, datetime.now(timezone.utc).replace(tzinfo=None)))
                category = "restored"
                result = dict(source_id=source_id, target_id=str(uuid.UUID(bytes=project_id)),
                              opportunity_date_utc=value.isoformat() + "Z")
            target.commit()
            report[category].append(result)
        except (ValueError, pymysql.MySQLError) as exception:
            target.rollback()
            report["failed"].append(dict(source_id=source_id, reason=type(exception).__name__))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-env", type=Path, required=True)
    parser.add_argument("--report-dir", type=Path, required=True)
    parser.add_argument("--backfill-opportunity-dates", action="store_true", help="仅给已有隔离迁移项目补回空日期，不覆盖已有值")
    args = parser.parse_args()
    target_url = os.environ.get("NDI_REHEARSAL_TARGET_URL")
    if not target_url:
        parser.error("必须显式设置 NDI_REHEARSAL_TARGET_URL")
    source_config = parse_url(source_url(args.source_env))
    target_config = parse_url(target_url)
    if target_config["database"] == "ndi" or not target_config["database"].startswith("ndi_rehearsal_"):
        parser.error("目标数据库名称必须以 ndi_rehearsal_ 开头，且不得为应用库 ndi")
    if (source_config["host"], source_config["port"], source_config["database"]) == (
            target_config["host"], target_config["port"], target_config["database"]):
        parser.error("源库与目标库不得相同")
    source = pymysql.connect(**source_config)
    target = pymysql.connect(**target_config)
    try:
        execute(source, "SET TRANSACTION READ ONLY")
        source_projects = fetch_all(source, """
            SELECT p.*, f.total_amount, f.network_amount, f.gewu_amount,
                   f.big_data_amount, f.wangwu_amount, f.contract_no, f.signed_amount
            FROM project p LEFT JOIN project_finance f ON f.project_id = p.id
            WHERE p.record_status = 'EFFECTIVE'
            ORDER BY p.created_at, p.id
        """)
        categories = defaultdict(list)
        members = defaultdict(list)
        progress = defaultdict(list)
        milestones = defaultdict(list)
        for table, destination in (("project_category", categories),
                                   ("project_member", members),
                                   ("project_progress", progress),
                                   ("stage_milestone", milestones)):
            for item in fetch_all(source, "SELECT * FROM " + table + " ORDER BY id"):
                destination[item["project_id"]].append(item)
        people = {item["id"]: item for item in fetch_all(source, "SELECT id,user_id FROM person_directory")}
        provinces = fetch_all(source, "SELECT id,code,name,enabled FROM province")
        cities = fetch_all(source, "SELECT id,province_id,name,enabled FROM city")
        users = fetch_all(source, """SELECT id,login_name,display_name,role_code,
                                  province_id,city_id,enabled FROM user_account""")
        source.commit()

        version = fetch_all(target, "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1")
        if not version or int(version[0]["version"]) < 31:
            raise ValueError("隔离目标库须先执行至 V31 的 Flyway 迁移")
        if args.backfill_opportunity_dates:
            report = backfill_legacy_opportunity_dates(target, source_projects)
            args.report_dir.mkdir(parents=True, exist_ok=True)
            (args.report_dir / "opportunity-date-backfill.json").write_text(
                json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps({key: len(value) for key, value in report.items()}, ensure_ascii=False))
            return
        if fetch_all(target, "SELECT id FROM project_project LIMIT 1"):
            raise ValueError("目标项目表非空；演练只允许写入全新的隔离库")
        industry_codes = {row["industry_name"]: row["industry_code"] for row in
                          fetch_all(target, "SELECT industry_name,industry_code FROM project_industry_reference WHERE enabled = 1")}
        target.rollback()

        # 仅在隔离库建立历史引用目录；不迁移口令、认证资料或业务审批。
        for item in provinces:
            execute(target, """INSERT INTO organization_province
                    (id,province_code,province_name,enabled) VALUES (%s,%s,%s,%s)""",
                    (binary(item["id"]), item["code"], item["name"], item["enabled"]))
        for item in cities:
            execute(target, """INSERT INTO organization_city
                    (id,province_id,city_code,city_name,enabled) VALUES (%s,%s,%s,%s,%s)""",
                    (binary(item["id"]), binary(item["province_id"]), item["id"].replace("-", ""),
                     item["name"], item["enabled"]))
        for item in users:
            execute(target, """INSERT INTO identity_user_account
                    (id,login_name,display_name,province_id,city_id,enabled)
                    VALUES (%s,%s,%s,%s,%s,%s)""",
                    (binary(item["id"]), item["login_name"], item["display_name"],
                     binary(item["province_id"]) if item["province_id"] else None,
                     binary(item["city_id"]) if item["city_id"] else None, item["enabled"]))
            execute(target, "INSERT INTO identity_user_role(user_id,role_code) VALUES (%s,%s)",
                    (binary(item["id"]), item["role_code"]))
        target.commit()

        failed = []
        successful = []
        seen = set()
        fallback_actors = 0
        for old in source_projects:
            source_id = old["id"]
            reasons = []
            if not isinstance(old.get("opportunity_date"), datetime):
                reasons.append("MISSING_OPPORTUNITY_DATE")
            key = (old["city_id"], norm(old["project_name"] or ""))
            if key in seen:
                reasons.append("DUPLICATE_AFTER_FIRST")
            else:
                seen.add(key)
            if not old["customer_name"] or not old["customer_name"].strip():
                reasons.append("MISSING_CUSTOMER_NAME")
            if not old["customer_industry"] or not old["customer_industry"].strip():
                reasons.append("MISSING_INDUSTRY")
            elif old["customer_industry"] not in industry_codes:
                reasons.append("INDUSTRY_NOT_MAPPED")
            if old["total_amount"] is None or old["total_amount"] < 0:
                reasons.append("INVALID_TOTAL_AMOUNT")
            if old["current_stage_code"] not in STAGES:
                reasons.append("UNKNOWN_STAGE")
            project_members = members[source_id]
            leaders = [people.get(member["person_id"], {}).get("user_id") for member in project_members
                       if member["role_code"] == "PROVINCE_LEADER"]
            leaders = [item for item in leaders if item]
            managers = [item["person_name_snapshot"] for item in project_members
                        if item["role_code"] == "CITY_ACCOUNT_MANAGER" and item["person_name_snapshot"]]
            if len(set(leaders)) != 1:
                reasons.append("PROVINCE_LEADER_NOT_UNIQUE")
            if not managers:
                reasons.append("MISSING_CITY_ACCOUNT_MANAGER")
            if not old["customer_requirement"] or not old["customer_requirement"].strip():
                reasons.append("MISSING_CUSTOMER_REQUIREMENT")
            selected = categories[source_id]
            if not selected:
                reasons.append("MISSING_CATEGORY")
            category_amounts = []
            for item in selected:
                code = item["category_code"]
                amount = old.get(AMOUNT_COLUMNS.get(code, ""))
                if code not in AMOUNT_COLUMNS or amount is None or amount < 0:
                    reasons.append("INVALID_CATEGORY_AMOUNT")
                else:
                    category_amounts.append((code, amount))
            if old["total_amount"] is not None and sum((a for _, a in category_amounts), Decimal()) > old["total_amount"]:
                reasons.append("CATEGORY_SUM_EXCEEDS_TOTAL")
            if reasons:
                failed.append(dict(source_id=source_id, project_code=old["project_code"], reasons=sorted(set(reasons))))
                continue
            try:
                project_id = fresh_id()
                creator_id = binary(old["creator_id"])
                insert_legacy_project(target, project_id, old, industry_codes[old["customer_industry"]],
                                      leaders[0], managers[0], creator_id)
                insert_legacy_responsible_people(target, project_id, project_members, leaders[0])
                for code, amount in category_amounts:
                    execute(target, "INSERT INTO project_category_amount(id,project_id,category_code,expected_amount) VALUES (%s,%s,%s,%s)",
                            (fresh_id(), project_id, code, amount))
                for item in project_members:
                    target_id = fresh_id()
                    execute(target, """INSERT INTO project_legacy_member_snapshot
                        (id,project_id,source_member_id,role_code,person_name,source_person_id)
                        VALUES (%s,%s,%s,%s,%s,%s)""",
                        (target_id, project_id, item["id"], item["role_code"], item["person_name_snapshot"], item["person_id"]))
                    add_mapping(target, "project_member", item["id"], project_id, target_id)
                for item in progress[source_id]:
                    target_id = fresh_id()
                    execute(target, """INSERT INTO project_progress
                        (id,project_id,progress_content,next_action,occurred_at,actor_id,owner_name,due_date)
                        VALUES (%s,%s,%s,%s,%s,%s,%s,%s)""",
                        (target_id, project_id, item["content"], item["next_action"] or "",
                         as_datetime(item["progress_date"]), binary(item["created_by"]),
                         item["owner_name"], item["due_date"]))
                    add_mapping(target, "project_progress", item["id"], project_id, target_id)
                for item in milestones[source_id]:
                    target_id = fresh_id()
                    actor_id = item["operator_id"] or old["creator_id"]
                    if not item["operator_id"]:
                        fallback_actors += 1
                    execute(target, """INSERT INTO project_stage_milestone
                        (id,project_id,stage_code,occurred_at,source_type,actor_id,description_text)
                        VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                        (target_id, project_id, item["stage_code"], as_datetime(item["occurred_on"]),
                         "LEGACY_" + item["source"], binary(actor_id), item["description"]))
                    add_mapping(target, "stage_milestone", item["id"], project_id, target_id)
                execute(target, """INSERT INTO project_source_record
                    (id,project_id,source_system,source_record_id,source_type,imported_at)
                    VALUES (%s,%s,'LEGACY_MYSQL',%s,%s,%s)""",
                    (fresh_id(), project_id, source_id, old["source_type"], datetime.now(timezone.utc).replace(tzinfo=None)))
                execute(target, """INSERT INTO project_change_event
                    (id,project_id,event_type,aggregate_version,payload,actor_id,occurred_at)
                    VALUES (%s,%s,'PROJECT_CREATED',0,%s,%s,%s)""",
                    (fresh_id(), project_id, json.dumps({"source": "LEGACY_MYSQL", "sourceId": source_id}),
                     creator_id, datetime.now(timezone.utc).replace(tzinfo=None)))
                target.commit()
                successful.append(dict(source_id=source_id, project_code=old["project_code"],
                                       target_id=str(uuid.UUID(bytes=project_id)), total_amount=old["total_amount"],
                                       stage=old["current_stage_code"]))
            except Exception as exception:
                target.rollback()
                failed.append(dict(source_id=source_id, project_code=old["project_code"],
                                   reasons=["TARGET_WRITE_FAILED", type(exception).__name__]))
        report = {
            "source_effective_count": len(source_projects),
            "source_total_amount": str(sum((p["total_amount"] or Decimal() for p in source_projects), Decimal())),
            "migrated_count": len(successful),
            "migrated_total_amount": str(sum((item["total_amount"] for item in successful), Decimal())),
            "target_count": fetch_all(target, "SELECT COUNT(*) AS n FROM project_project")[0]["n"],
            "target_total_amount": str(fetch_all(target, "SELECT COALESCE(SUM(total_amount),0) AS amount FROM project_project")[0]["amount"]),
            "source_stage_counts": dict(Counter(p["current_stage_code"] for p in source_projects)),
            "migrated_stage_counts": dict(Counter(p["stage"] for p in successful)),
            "migrated_progress_count": fetch_all(target, "SELECT COUNT(*) AS n FROM project_progress")[0]["n"],
            "migrated_milestone_count": fetch_all(target, "SELECT COUNT(*) AS n FROM project_stage_milestone")[0]["n"],
            "migrated_member_count": fetch_all(target, "SELECT COUNT(*) AS n FROM project_legacy_member_snapshot")[0]["n"],
            "milestone_actor_fallback_count": fallback_actors,
            "failed_count": len(failed),
        }
        if report["target_count"] != report["migrated_count"] or report["target_total_amount"] != report["migrated_total_amount"]:
            raise AssertionError("目标数量或金额与成功映射不一致")
        args.report_dir.mkdir(parents=True, exist_ok=True)
        (args.report_dir / "legacy-migration-rehearsal.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        with (args.report_dir / "legacy-migration-failures.csv").open("w", newline="", encoding="utf-8-sig") as output:
            writer = csv.writer(output)
            writer.writerow(("旧项目ID", "项目编号", "失败原因"))
            for item in failed:
                writer.writerow((item["source_id"], item["project_code"], ";".join(item["reasons"])))
        with (args.report_dir / "legacy-migration-id-map.csv").open("w", newline="", encoding="utf-8-sig") as output:
            writer = csv.writer(output)
            writer.writerow(("旧项目ID", "项目编号", "新项目ID"))
            for item in successful:
                writer.writerow((item["source_id"], item["project_code"], item["target_id"]))
        print(json.dumps(report, ensure_ascii=False, indent=2))
    finally:
        source.close()
        target.close()


def add_mapping(target, table, source_id, project_id, target_id):
    execute(target, """INSERT INTO project_legacy_record_map
        (source_table,source_record_id,project_id,target_record_id) VALUES (%s,%s,%s,%s)""",
        (table, source_id, project_id, target_id))


if __name__ == "__main__":
    try:
        main()
    except Exception as exception:
        print("迁移演练失败：" + str(exception), file=sys.stderr)
        sys.exit(1)
