package com.pluto.ndi.projects.domain;

/** 项目建设原文、产品推广内容和备注组成一组可选说明资料。 */
public record ProjectNarrative(
        String constructionContentRaw,
        String productPromotionContent,
        String remark
) { }
