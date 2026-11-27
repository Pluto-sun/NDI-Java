package com.pluto.ndi.administration.domain;

import java.util.Arrays;
import java.util.List;

public enum ListColumnCatalog {
    REVENUE("计收列表", columns(
            "fee-month:费用账期|province:省份|city:地市／组织|source-project:来源项目|category:三化分类|customer:客户|contract:合同编号|clause:条款编号|clause-amount:条款金额（元）|source-amount:来源本月计收（元）|recognized-amount:认定金额（元）|reported-project:通报项目名称|integrated-province:对应省分（集成）|state:办理状态")),
    PROJECTS("项目列表", columns(
            "name:项目名称|location:归属|customer:客户|stage:阶段|category:类别|signing-subject:签约主体|total:预计总金额|owners:项目负责人|month:项目所属月份|industry:行业")),
    PRODUCTION("排产计划", columns(
            "reviewer:接口人|month:排产月份|location:省份 / 地市|project:项目名称 / 客户名称|category:三化类别|NETWORK:5G工业专网|GEWU:格物工业互联网平台|BIG_DATA:工业大数据平台|WANGWU:万悟工业智能体平台|state:项目状态|introduction:产品简介|remark:备注")),
    GEWU("已部署格物项目清单", columns(
            "项目名称|客户名称|省份|地市|接口人|制造类型|国民经济行业分类|所属行业|部署版本|上一周期是否已接设备|物模型总数|接入设备总数量|协议数量|日均上报数据量|设备接入日期|是否最新工具包|是否可升级工具包|备注")),
    WANGWU("万悟部署情况", columns(
            "接口人|省分|部署地点|具体名称|部署进度|部署版本|版本更新日期|updated-at:更新时间")),
    AGENTS_LIVE("已上线智能体指标追踪", columns(
            "接口人|省分|省分智能化接口人|环节|智能体名称|应用项目名称|最终客户名称|智能体包含组件情况|是否为三方生态迁移|是否计收|是否在计收表中出现|上线月份|计收月份|updated-at:更新时间")),
    AGENTS_DEVELOPMENT("开发中智能体指标追踪", columns(
            "接口人|省分|省分智能化接口人|环节|智能体名称|应用项目名称|最终客户名称|智能体包含组件情况|状态|是否为三方生态迁移|开发进度|智能体开发开始时间|是否计收|计收月份|updated-at:更新时间"));

    public static final int MAX_FIXED = 4;

    public record Field(String id, String label) {}

    private final String title;
    private final List<Field> fields;

    ListColumnCatalog(String title, List<Field> fields) {
        this.title = title;
        this.fields = fields;
    }

    public String title() { return title; }
    public List<Field> fields() { return fields; }

    public boolean contains(String field) {
        return fields.stream().anyMatch(item -> item.id().equals(field));
    }

    private static List<Field> columns(String definitions) {
        // 能力台账沿用字段存储键，展示名称可以独立调整。
        return Arrays.stream(definitions.split("\\|")).map(definition -> {
            var parts = definition.split(":", 2);
            return new Field(parts[0], parts.length == 1 ? parts[0] : parts[1]);
        }).toList();
    }
}
