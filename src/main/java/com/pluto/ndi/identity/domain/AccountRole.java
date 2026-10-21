package com.pluto.ndi.identity.domain;

/** 可配置角色目录；同一账号仅允许组合相同省分范围的三类负责人角色。 */
public enum AccountRole {
    PROVINCE_REPORTER("省分三化负责人", Scope.PROVINCE),
    PROVINCE_DIGITAL_LEADER("省分数字化负责人", Scope.PROVINCE),
    PROVINCE_INTELLIGENT_LEADER("省分智能化负责人", Scope.PROVINCE),
    CITY_REPORTER("地市三化负责人", Scope.CITY),
    INTERFACE_REVIEWER("接口人", Scope.REVIEWER),
    GROUP_REVIEWER("集团", Scope.NATIONAL),
    DS_TECH("数科", Scope.NATIONAL),
    DS_INTELLIGENCE("数智", Scope.NATIONAL),
    GD_DPASS("广东 DPASS", Scope.NATIONAL),
    SYSTEM_ADMIN("系统管理员", Scope.NATIONAL);

    public enum Scope { PROVINCE, CITY, REVIEWER, NATIONAL }
    private final String label;
    private final Scope scope;
    AccountRole(String label, Scope scope) { this.label = label; this.scope = scope; }
    public String label() { return label; }
    public Scope scope() { return scope; }
}
