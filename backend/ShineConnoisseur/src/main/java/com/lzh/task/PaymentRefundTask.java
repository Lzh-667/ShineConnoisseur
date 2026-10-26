package com.lzh.task;

import com.lzh.mapper.PaymentRefundMapper;
import com.lzh.service.PaymentRefundProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.refund-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRefundTask {
    private final PaymentRefundMapper refundMapper;
    private final PaymentRefundProcessingService processingService;

    @Scheduled(initialDelayString = "${payment.refund-initial-delay-ms:15000}",
            fixedDelayString = "${payment.refund-scan-delay-ms:60000}")
    public void processRefunds() {
        for (var refund : refundMapper.due(LocalDateTime.now())) {
            try { processingService.process(refund.getId()); }
            catch (RuntimeException e) { log.error("退款任务异常，refundId={}", refund.getId(), e); }
        }
    }
}
