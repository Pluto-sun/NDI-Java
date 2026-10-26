package com.pluto.ndi.projects.domain;

import com.pluto.ndi.shared.web.ErrorCode;

import org.springframework.http.HttpStatus;

/** 项目模块对外暴露的稳定错误码。 */
public enum ProjectErrorCode implements ErrorCode {
    IMPORT_TASK_CONFLICT(
            HttpStatus.CONFLICT,
            "PROJECT_IMPORT_TASK_CONFLICT",
            "导入任务已变化",
            "请重新打开导入任务，不能跨步骤跳转或覆盖其他页面的更改"),
    IMPORT_NAME_REVIEW_REQUIRED(
            HttpStatus.CONFLICT,
            "PROJECT_IMPORT_NAME_REVIEW_REQUIRED",
            "请处理相似项目",
            "高度相似项目须填写至少5字差异说明，其他相似项目须确认不是同一项目"),
    INVALID_RESPONSIBLE_PERSONS(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_RESPONSIBLE_PERSONS",
            "项目负责人无效",
            "每项目须有1至100位负责人，姓名最长100字；省分负责人至多一位，绑定账号不得重复且须符合资格"),
    INVALID_ATTRIBUTION_FILTER(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_ATTRIBUTION_FILTER",
            "所属月份筛选无效",
            "请选择有效月份区间，最多36个月；阶段和属地筛选不得包含无效值"),
    INVALID_CREATION_ATTRIBUTES(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_CREATION_ATTRIBUTES",
            "创建可选属性无效",
            "重要性代码最长30字，首条进展负责人最长100字"),
    INVALID_EXPORT_FIELDS(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_EXPORT_FIELDS", "导出字段无效", "至少选择一个合法字段，字段不能重复"),
    INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_DATE_RANGE",
            "项目日期范围无效",
            "日期必须位于1000年至9999年，且起始日期不得晚于结束日期"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在", "未找到指定项目"),
    ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_FORBIDDEN", "无权访问项目", "当前账号无权访问项目数据"),
    PROGRESS_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PROJECT_PROGRESS_NOT_FOUND", "项目进展不存在", "更正目标不是本项目的现有进展"),
    TERMINATED_READ_ONLY(
            HttpStatus.CONFLICT,
            "PROJECT_TERMINATED_READ_ONLY",
            "已终止项目只读",
            "已终止项目仅允许读取历史数据，不接受业务写入"),
    INVALID_PROGRESS(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_PROGRESS",
            "项目进展内容无效",
            "进展正文和下一步动作必须填写且长度不得超过限制"),
    INVALID_PROFILE(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_PROFILE", "项目普通信息无效", "客户、行业、需求及责任字段必须符合填写规则"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "PROJECT_VERSION_CONFLICT", "项目版本冲突", "项目数据已变化，请刷新后重试"),
    CREATION_FORBIDDEN(
            HttpStatus.FORBIDDEN, "PROJECT_CREATION_FORBIDDEN", "无权创建项目", "当前账号无权在目标属地创建项目"),
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_DRAFT_NOT_FOUND", "草稿不存在", "草稿不存在或不属于当前账号"),
    INVALID_DRAFT(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_DRAFT", "项目草稿无效", "草稿必须是有效表单对象且不得超过大小限制"),
    INVALID_PROJECT_NAME(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_PROJECT_NAME", "项目名称无效", "项目名称不能为空或只有标点符号"),
    DUPLICATE_PROJECT(HttpStatus.CONFLICT, "PROJECT_DUPLICATE", "项目名称重复", "同地市已有名称相同的项目，请进入现有项目维护"),
    DUPLICATE_CONFIRMATION_REQUIRED(
            HttpStatus.CONFLICT,
            "PROJECT_DUPLICATE_CONFIRMATION_REQUIRED",
            "发现相似项目",
            "确认另建项目时必须填写原因"),
    INVALID_ORGANIZATION(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_ORGANIZATION", "项目属地无效", "省分与地市不存在、未启用或不匹配"),
    INVALID_PROVINCE_LEADER(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_PROVINCE_LEADER",
            "省分负责人无效",
            "省分负责人必须是本省已启用的省分负责人账号"),
    INVALID_SIGNING_SUBJECT(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_SIGNING_SUBJECT",
            "签约主体无效",
            "签约主体必须为省分签约或数科签约"),
    INVALID_SUPPORT_EXPERTS(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_SUPPORT_EXPERTS",
            "支撑专家无效",
            "支撑专家姓名不能超过100字或重复"),
    INVALID_PLANNING_FIELDS(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_PLANNING_FIELDS",
            "项目规划属性无效",
            "应用场景必须使用有效代码且不得重复；拟招标日期应为有效月份"),
    INVALID_NARRATIVE(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_NARRATIVE",
            "项目补充资料无效",
            "建设内容和产品推广内容不得超过4000字，备注不得超过10000字"),
    INVALID_INDUSTRY(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_INDUSTRY", "客户所属行业无效", "客户所属行业必须使用已启用的工业行业代码"),
    INVALID_CATEGORY_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_CATEGORY_AMOUNT",
            "三化类别金额无效",
            "三化类别金额必须完整、非负且合计不得超过项目总金额"),
    INVALID_MAJOR_CHANGE(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_MAJOR_CHANGE",
            "重大变更内容无效",
            "变更内容必须符合当前项目阶段和金额规则"),
    APPROVER_INVALID(
            HttpStatus.BAD_REQUEST,
            "PROJECT_APPROVER_INVALID",
            "审批人无效",
            "审批人必须具有目标省份的对应审批职责且不能是发起人"),
    APPROVAL_FORBIDDEN(
            HttpStatus.FORBIDDEN, "PROJECT_APPROVAL_FORBIDDEN", "无权处理审批", "仅申请指定的审批人或发起人有权执行对应操作"),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_APPROVAL_NOT_FOUND", "审批申请不存在", "未找到指定审批申请"),
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PROJECT_NOTIFICATION_NOT_FOUND", "审批通知不存在", "未找到当前账号的指定通知"),
    APPROVAL_NOT_PENDING(
            HttpStatus.CONFLICT, "PROJECT_APPROVAL_NOT_PENDING", "审批申请已结束", "申请不再处于待审批状态"),
    PENDING_APPROVAL_EXISTS(
            HttpStatus.CONFLICT,
            "PROJECT_PENDING_APPROVAL_EXISTS",
            "已有待审批申请",
            "同一项目同一时间只能存在一条待审批申请"),
    APPROVAL_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "PROJECT_APPROVAL_VERSION_CONFLICT",
            "项目版本冲突",
            "项目数据已变化，请刷新后重新发起申请"),
    INVALID_STAGE_ADVANCEMENT(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_STAGE_ADVANCEMENT",
            "阶段推进内容无效",
            "只能申请相邻正向阶段，且阶段日期与字段必须完整"),
    STAGE_EVIDENCE_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "PROJECT_STAGE_EVIDENCE_REQUIRED",
            "阶段材料无效",
            "必须提供属于本项目且已完成上传校验的指定材料"),
    DOCUMENT_IN_USE(HttpStatus.CONFLICT, "PROJECT_DOCUMENT_IN_USE", "项目资料已被引用", "审批申请已关联该资料，不允许删除"),
    INACTIVITY_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PROJECT_INACTIVITY_NOT_FOUND", "督办事项不存在", "未找到当前账号有权处理的督办事项"),
    INACTIVITY_NOT_ACTIONABLE(
            HttpStatus.CONFLICT, "PROJECT_INACTIVITY_NOT_ACTIONABLE", "督办事项已处理", "督办已转发或已闭环，请刷新列表"),
    INACTIVITY_INVALID_FORWARD(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INACTIVITY_INVALID_FORWARD",
            "督办转发无效",
            "请选择符合职责和属地的接收人，并填写不超过2000字的备注"),
    INACTIVITY_INVALID_TRIGGER(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INACTIVITY_INVALID_TRIGGER",
            "督办触发参数无效",
            "手动演练仅支持30、33或36天"),
    CONTRACT_NO_CONFLICT(
            HttpStatus.CONFLICT, "PROJECT_CONTRACT_NO_CONFLICT", "合同编号重复", "合同编号必须在全系统唯一"),
    EXPORT_TOO_LARGE(
            HttpStatus.BAD_REQUEST, "PROJECT_EXPORT_TOO_LARGE", "导出数据过多", "导出结果不得超过1000条，请缩小筛选范围"),
    EXPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_EXPORT_NOT_FOUND", "导出任务不存在", "未找到指定的项目导出任务"),
    EXPORT_NOT_READY(HttpStatus.CONFLICT, "PROJECT_EXPORT_NOT_READY", "导出文件尚未就绪", "请在任务完成后下载导出文件"),
    IMPORT_INVALID_FILE(
            HttpStatus.BAD_REQUEST, "PROJECT_IMPORT_INVALID_FILE", "导入文件无效", "请检查模板、文件大小和工作表内容"),
    IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_IMPORT_NOT_FOUND", "导入批次不存在", "批次不存在或不属于当前账号"),
    IMPORT_ALREADY_CONFIRMED(
            HttpStatus.CONFLICT, "PROJECT_IMPORT_ALREADY_CONFIRMED", "导入批次已确认", "同一批次只能确认一次"),
    IMPORT_NOT_PREVIEWED(
            HttpStatus.CONFLICT, "PROJECT_IMPORT_NOT_PREVIEWED", "导入计划状态不允许操作", "仅预检中的导入计划可确认或取消"),
    IMPORT_SELECTION_INVALID(
            HttpStatus.BAD_REQUEST,
            "PROJECT_IMPORT_SELECTION_INVALID",
            "导入行选择无效",
            "只能选择当前批次中通过预检的行，且至少选择一行"),
    IMPORT_ROW_INVALID(
            HttpStatus.BAD_REQUEST,
            "PROJECT_IMPORT_ROW_INVALID",
            "导入行修正无效",
            "只能修改原模板已有的字段，且字段值不得超过 4000 字符"),
    IMPORT_ROW_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "PROJECT_IMPORT_ROW_VERSION_CONFLICT",
            "导入行版本冲突",
            "导入行已被修改，请刷新计划后重试"),
    IMPORT_ROW_NOT_EDITABLE(
            HttpStatus.CONFLICT,
            "PROJECT_IMPORT_ROW_NOT_EDITABLE",
            "导入行不可修正",
            "该行缺少原始模板字段或计划已进入确认流程，请重新上传文件"),
    IMPORT_REMAP_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "PROJECT_IMPORT_REMAP_NOT_ALLOWED",
            "导入计划不能重新映射",
            "已有项目入库或正在导入的计划不能重新映射，请刷新计划状态"),
    IMPORT_REMAP_FILE_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PROJECT_IMPORT_REMAP_FILE_MISMATCH",
            "重新映射文件不一致",
            "请重新选择该导入计划使用的同一原始 Excel 文件"),
    IMPORT_RECOVERY_NOT_READY(
            HttpStatus.CONFLICT,
            "PROJECT_IMPORT_RECOVERY_NOT_READY",
            "导入恢复尚不可用",
            "批次开始确认不足 15 分钟，请稍后刷新或重试"),
    SNAPSHOT_NOT_READY(
            HttpStatus.NOT_FOUND, "PROJECT_SNAPSHOT_NOT_READY", "月末快照尚未生成", "该月份未生成可查询的月末快照"),
    INVALID_SNAPSHOT_MONTH(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_SNAPSHOT_MONTH",
            "月末查询范围无效",
            "趋势查询起止月份必须有序且不超过 24 个月"),
    INVALID_SNAPSHOT_FILTER(
            HttpStatus.BAD_REQUEST,
            "PROJECT_INVALID_SNAPSHOT_FILTER",
            "月末筛选条件无效",
            "默认口径不含已终止项目；如需筛选已终止阶段，请显式纳入已终止项目"),
    INVALID_MAP_ADCODE(
            HttpStatus.BAD_REQUEST, "PROJECT_INVALID_MAP_ADCODE", "地图编码无效", "地图边界必须使用六位行政区划代码"),
    MAP_BOUNDARY_UNAVAILABLE(
            HttpStatus.BAD_GATEWAY,
            "PROJECT_MAP_BOUNDARY_UNAVAILABLE",
            "地图边界不可用",
            "地图边界服务暂不可用，请稍后重试");

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String message;

    ProjectErrorCode(HttpStatus status, String code, String title, String message) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String message() {
        return message;
    }
}
