package com.pluto.ndi.projects.domain;

import java.math.BigDecimal;
import java.util.Collection;

import com.pluto.ndi.projects.domain.entity.ProjectCategoryAmountEntity;

/** 四类固定项目分类金额，独立保存于月末状态以避免历史报表随主档漂移。 */
public record ProjectCategoryTotals(BigDecimal network, BigDecimal gewu,
                                    BigDecimal bigData, BigDecimal wangwu) {

    public static ProjectCategoryTotals from(Collection<ProjectCategoryAmountEntity> amounts) {
        var network = BigDecimal.ZERO;
        var gewu = BigDecimal.ZERO;
        var bigData = BigDecimal.ZERO;
        var wangwu = BigDecimal.ZERO;
        for (var item : amounts) {
            switch (item.categoryCode()) {
                case "NETWORK" -> network = network.add(item.expectedAmount());
                case "GEWU" -> gewu = gewu.add(item.expectedAmount());
                case "BIG_DATA" -> bigData = bigData.add(item.expectedAmount());
                case "WANGWU" -> wangwu = wangwu.add(item.expectedAmount());
                default -> throw new IllegalArgumentException("未知项目分类：" + item.categoryCode());
            }
        }
        return new ProjectCategoryTotals(network, gewu, bigData, wangwu);
    }
}
