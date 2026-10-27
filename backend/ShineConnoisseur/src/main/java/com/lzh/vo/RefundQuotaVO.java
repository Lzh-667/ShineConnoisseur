package com.lzh.vo;

/** 北京时间自然年额度；按申请受理时间归属，处理中已占额度。 */
public record RefundQuotaVO(int year, int limit, long used, long remaining) {}
