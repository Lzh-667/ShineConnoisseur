package com.lzh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzh.common.BusinessException;
import com.lzh.mapper.*;
import com.lzh.po.*;
import com.lzh.service.PaymentRefundService;
import com.lzh.vo.PaymentOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import com.lzh.vo.RefundQuotaVO;
import java.util.List;
import java.util.Objects;
import static com.lzh.utils.SystemConstants.*;

@Service
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {
    private static final int ANNUAL_LIMIT = 3;
    private final Clock refundClock;
    private final PaymentOrderMapper orderMapper;
    private final PaymentRefundMapper refundMapper;
    private final UserVipMapper vipMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRefund request(Long userId, String orderNo) {
        if (userId == null || StrUtil.isBlank(orderNo)) throw new BusinessException("订单号不能为空");
        if (refundMapper.lockUser(userId) == null) throw new BusinessException("用户不存在");
        PaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId).eq(PaymentOrder::getOrderNo, orderNo).last("FOR UPDATE"));
        if (order == null) throw new BusinessException("订单不存在");
        PaymentRefund existing = refundMapper.findByOrder(order.getId());
        // 重放受理请求不重新检查24小时，渠道处理时间不影响已经受理的退款。
        if (existing != null) return existing;
        LocalDateTime now = now();
        LocalDateTime yearStart = now.withDayOfYear(1).toLocalDate().atStartOfDay();
        // 持有用户锁后使用当前读；即使外层事务已有旧快照，也不能绕过额度。
        if (refundMapper.annualUsageForUpdate(userId, yearStart, yearStart.plusYears(1)).size() >= ANNUAL_LIMIT)
            throw new BusinessException("本年度退款次数已达3次上限");
        PaymentOrder latest = orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId).isNotNull(PaymentOrder::getPayTime)
                .orderByDesc(PaymentOrder::getPayTime).orderByDesc(PaymentOrder::getId).last("LIMIT 1 FOR UPDATE"));
        if (latest == null || !Objects.equals(latest.getId(), order.getId()))
            throw new BusinessException("仅最近一笔已支付订单可以退款");
        if (!Objects.equals(order.getStatus(), ORDER_STATUS_SUCCESS))
            throw new BusinessException("订单当前状态不允许申请退款");
        if (order.getPayTime() == null || order.getPayTime().isAfter(now)
                || now.isAfter(order.getPayTime().plusHours(24)))
            throw new BusinessException("订单已超过支付成功后24小时的退款期限");
        if (refundMapper.pendingCount(userId) > 0) throw new BusinessException("已有退款正在处理中");
        if (order.getDurationDays() == null || order.getDurationDays() <= 0
                || order.getAmount() == null || order.getAmount().signum() <= 0)
            throw new BusinessException("订单权益或金额快照异常，请联系客服");

        UserVip vip = vipMapper.selectOne(new LambdaQueryWrapper<UserVip>()
                .eq(UserVip::getUserId, userId).last("FOR UPDATE"));
        if (vip == null || vip.getExpireTime() == null)
            throw new BusinessException("权益记录异常，请联系客服");
        PaymentRefund refund = new PaymentRefund();
        refund.setOrderId(order.getId());
        refund.setUserId(userId);
        refund.setOrderNo(orderNo);
        refund.setRefundNo("UR" + orderNo);
        refund.setAmount(order.getAmount());
        refund.setDurationDays(order.getDurationDays());
        refund.setStatus("PROCESSING");
        refund.setEntitlementExpireBefore(vip.getExpireTime());
        refund.setEntitlementExpireAfter(vip.getExpireTime().minusDays(order.getDurationDays()));
        refund.setRequestedAt(now);
        refund.setNextRetryAt(now);
        if (refundMapper.insert(refund) != 1) throw new BusinessException("退款申请保存失败");
        if (orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId()).eq(PaymentOrder::getStatus, ORDER_STATUS_SUCCESS)
                .set(PaymentOrder::getStatus, ORDER_STATUS_REFUNDING)) != 1)
            throw new BusinessException("订单状态已变化");
        return refund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long refundId) {
        PaymentRefund snapshot = refundMapper.selectById(refundId);
        if (snapshot == null) throw new BusinessException("退款记录不存在");
        refundMapper.lockUser(snapshot.getUserId());
        PaymentRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getId, refundId).last("FOR UPDATE"));
        if ("SUCCESS".equals(refund.getStatus())) return;
        if (!"PROCESSING".equals(refund.getStatus())) throw new BusinessException("退款状态异常");
        PaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, refund.getOrderId()).last("FOR UPDATE"));
        if (order == null || !Objects.equals(order.getStatus(), ORDER_STATUS_REFUNDING))
            throw new BusinessException("退款订单状态异常");
        // 退款处理中禁止新的权益发放，精确匹配快照；不覆盖管理员或其他入口的意外修改。
        if (vipMapper.update(null, new LambdaUpdateWrapper<UserVip>()
                .eq(UserVip::getUserId, refund.getUserId())
                .eq(UserVip::getExpireTime, refund.getEntitlementExpireBefore())
                .set(UserVip::getExpireTime, refund.getEntitlementExpireAfter())) != 1)
            throw new BusinessException("权益快照已变化，需要人工核对退款");
        LocalDateTime now = now();
        if (orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, refund.getOrderId()).eq(PaymentOrder::getStatus, ORDER_STATUS_REFUNDING)
                .set(PaymentOrder::getStatus, ORDER_STATUS_REFUND)) != 1)
            throw new BusinessException("退款订单更新失败");
        if (refundMapper.update(null, new LambdaUpdateWrapper<PaymentRefund>()
                .eq(PaymentRefund::getId, refundId).eq(PaymentRefund::getStatus, "PROCESSING")
                .set(PaymentRefund::getStatus, "SUCCESS").set(PaymentRefund::getCompletedAt, now)
                .set(PaymentRefund::getRightsRevokedAt, now).set(PaymentRefund::getLastError, null)) != 1)
            throw new BusinessException("退款结果保存失败");
    }

    private PaymentOrder latestPaid(Long userId) {
        // 不排除已退款订单，防止最新一单退款后逐笔向前退款。
        return orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId).isNotNull(PaymentOrder::getPayTime)
                .orderByDesc(PaymentOrder::getPayTime).orderByDesc(PaymentOrder::getId).last("LIMIT 1"));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(refundClock.instant(), ZoneId.of("Asia/Shanghai"));
    }

    @Override
    public RefundQuotaVO quota(Long userId) {
        return quota(userId, now());
    }

    private RefundQuotaVO quota(Long userId, LocalDateTime now) {
        LocalDateTime start = now.withDayOfYear(1).toLocalDate().atStartOfDay();
        long used = refundMapper.annualUsage(userId, start, start.plusYears(1));
        return new RefundQuotaVO(now.getYear(), ANNUAL_LIMIT, used, Math.max(0, ANNUAL_LIMIT - used));
    }

    @Override
    public List<PaymentOrderVO> recentOrders(Long userId) {
        PaymentOrder latest = latestPaid(userId);
        LocalDateTime now = now();
        boolean hasQuota = quota(userId, now).remaining() > 0;
        boolean pending = refundMapper.pendingCount(userId) > 0;
        return orderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId).orderByDesc(PaymentOrder::getId).last("LIMIT 20"))
                .stream().map(order -> {
                    PaymentOrderVO vo = BeanUtil.copyProperties(order, PaymentOrderVO.class);
                    vo.setRefundDeadline(order.getPayTime() == null ? null : order.getPayTime().plusHours(24));
                    vo.setRefundEligible(hasQuota && !pending && latest != null && latest.getId().equals(order.getId())
                            && Objects.equals(order.getStatus(), ORDER_STATUS_SUCCESS)
                            && !order.getPayTime().isAfter(now) && !now.isAfter(vo.getRefundDeadline()));
                    PaymentRefund refund = refundMapper.findByOrder(order.getId());
                    if (refund != null) {
                        vo.setRefundNo(refund.getRefundNo());
                        vo.setRefundRequestedAt(refund.getRequestedAt());
                        vo.setRefundCompletedAt(refund.getCompletedAt());
                    }
                    return vo;
                }).toList();
    }
}
