-- 切换审核角色时撤回旧流转，生效认定与认定历史保持原值。
START TRANSACTION;
SET @revenue_cutover_at = CURRENT_TIMESTAMP(6);
SET @revenue_cutover_reason = '计收审核流程切换，旧待审申请已撤回，请由接口人重新发起';

INSERT INTO system_audit_event (id, actor_id, event_type, resource_type, resource_id, detail, occurred_at)
SELECT UUID_TO_BIN(UUID()), NULL, 'REVENUE_WITHDRAWN', 'REVENUE_REQUEST', r.id,
       JSON_OBJECT('entryId', BIN_TO_UUID(r.entry_id), 'opinion', @revenue_cutover_reason,
                   'previousApproverId', BIN_TO_UUID(r.approver_id)), @revenue_cutover_at
FROM revenue_recognition_request r WHERE r.status = 'PENDING';

UPDATE revenue_notification n JOIN revenue_recognition_request r ON r.id = n.request_id
SET n.read_at = @revenue_cutover_at
WHERE r.status = 'PENDING' AND n.read_at IS NULL AND n.kind IN ('SUBMITTED','REASSIGNED');

INSERT INTO revenue_notification (id, entry_id, recipient_id, request_id, kind, message, created_at)
SELECT UUID_TO_BIN(UUID()), r.entry_id, r.initiator_id, r.id, 'WITHDRAWN', @revenue_cutover_reason, @revenue_cutover_at
FROM revenue_recognition_request r WHERE r.status = 'PENDING'
UNION ALL
SELECT UUID_TO_BIN(UUID()), r.entry_id, r.approver_id, r.id, 'WITHDRAWN', @revenue_cutover_reason, @revenue_cutover_at
FROM revenue_recognition_request r WHERE r.status = 'PENDING' AND r.approver_id <> r.initiator_id;

UPDATE revenue_entry e JOIN revenue_recognition_request r ON r.entry_id = e.id
SET e.version_no = e.version_no + 1, e.updated_at = @revenue_cutover_at
WHERE r.status = 'PENDING';

UPDATE revenue_recognition_request
SET status = 'WITHDRAWN', opinion = @revenue_cutover_reason,
    version_no = version_no + 1, decided_at = @revenue_cutover_at
WHERE status = 'PENDING';
COMMIT;
