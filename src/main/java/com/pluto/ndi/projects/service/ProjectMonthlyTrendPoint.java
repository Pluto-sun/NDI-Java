package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;

/** 一个已生成月份的项目经营趋势点。 */
public record ProjectMonthlyTrendPoint(YearMonth month, long projectCount, BigDecimal totalExpectedAmount) { }
