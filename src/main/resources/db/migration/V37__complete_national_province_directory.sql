-- 补齐省级目录，不修改已有组织标识或业务归属；行政简称和全称视为同一目录。
INSERT INTO organization_province (id, province_code, province_name, enabled)
SELECT UUID_TO_BIN(UUID()), source.code, source.name, TRUE
FROM (
    SELECT '110000' code, '北京市' name, '北京' short_name
    UNION ALL SELECT '120000','天津市','天津'
    UNION ALL SELECT '130000','河北省','河北'
    UNION ALL SELECT '140000','山西省','山西'
    UNION ALL SELECT '150000','内蒙古自治区','内蒙古'
    UNION ALL SELECT '210000','辽宁省','辽宁'
    UNION ALL SELECT '220000','吉林省','吉林'
    UNION ALL SELECT '230000','黑龙江省','黑龙江'
    UNION ALL SELECT '310000','上海市','上海'
    UNION ALL SELECT '320000','江苏省','江苏'
    UNION ALL SELECT '330000','浙江省','浙江'
    UNION ALL SELECT '340000','安徽省','安徽'
    UNION ALL SELECT '350000','福建省','福建'
    UNION ALL SELECT '360000','江西省','江西'
    UNION ALL SELECT '370000','山东省','山东'
    UNION ALL SELECT '410000','河南省','河南'
    UNION ALL SELECT '420000','湖北省','湖北'
    UNION ALL SELECT '430000','湖南省','湖南'
    UNION ALL SELECT '440000','广东省','广东'
    UNION ALL SELECT '450000','广西壮族自治区','广西'
    UNION ALL SELECT '460000','海南省','海南'
    UNION ALL SELECT '500000','重庆市','重庆'
    UNION ALL SELECT '510000','四川省','四川'
    UNION ALL SELECT '520000','贵州省','贵州'
    UNION ALL SELECT '530000','云南省','云南'
    UNION ALL SELECT '540000','西藏自治区','西藏'
    UNION ALL SELECT '610000','陕西省','陕西'
    UNION ALL SELECT '620000','甘肃省','甘肃'
    UNION ALL SELECT '630000','青海省','青海'
    UNION ALL SELECT '640000','宁夏回族自治区','宁夏'
    UNION ALL SELECT '650000','新疆维吾尔自治区','新疆'
    UNION ALL SELECT '710000','台湾省','台湾'
    UNION ALL SELECT '810000','香港特别行政区','香港'
    UNION ALL SELECT '820000','澳门特别行政区','澳门'
) source
WHERE NOT EXISTS (SELECT 1 FROM organization_province province
    WHERE province.province_code = source.code OR province.province_name IN (source.name, source.short_name));
