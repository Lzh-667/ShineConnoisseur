package com.lzh.payment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.lzh.common.BusinessException;
import com.lzh.mapper.*;
import com.lzh.po.PaymentRefund;
import com.lzh.service.*;
import com.lzh.service.impl.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.lzh.utils.SystemConstants.*;

/** 独立MySQL、真实Mapper和Spring事务；仅支付渠道使用mock。测试本身没有@Transactional。 */
@EnabledIfSystemProperty(named = "payment.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentRefundMysqlTest {
    @Configuration @EnableTransactionManagement
    static class Config {}

    @Intercepts(@Signature(type = Executor.class, method = "update",
            args = {MappedStatement.class, Object.class}))
    static class FaultInjector implements Interceptor {
        final AtomicBoolean failGrant = new AtomicBoolean();
        final AtomicBoolean failRevoke = new AtomicBoolean();
        @Override public Object intercept(Invocation invocation) throws Throwable {
            Object result = invocation.proceed(); // 先执行真实SQL，再故障注入
            String id = ((MappedStatement) invocation.getArgs()[0]).getId();
            if (id.endsWith("UserVipMapper.grantOrExtend") && failGrant.getAndSet(false)
                    || id.endsWith("UserVipMapper.update") && failRevoke.getAndSet(false))
                throw new IllegalStateException("test: failure after real VIP write");
            return result;
        }
    }

    private JdbcTemplate jdbc;
    private AnnotationConfigApplicationContext context;
    private PaymentRefundService refunds;
    private PaymentFulfillmentService fulfillment;
    private PaymentOrderStateService states;
    private PaymentRefundProcessingService processing;
    private PaymentRefundMapper refundMapper;
    private PaymentOrderMapper orders;
    private WechatPayService wechat;
    private AlipayService alipay;
    private final FaultInjector faults = new FaultInjector();
    private Long userId;
    private Long productId;
    private LocalDateTime baseline;

    @BeforeAll void connect() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:23307/shine_payment_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                "payment_it", "payment_it_only");
        jdbc = new JdbcTemplate(ds);
        assertEquals("shine_payment_it", jdbc.queryForObject("SELECT DATABASE()", String.class));
        for (String name : new String[]{"create_user", "create_vip_product", "create_user_vip", "create_payment_order",
                "migrate_payment_refund"}) {
            String sql = new ClassPathResource("db/" + name + ".sql").getContentAsString(StandardCharsets.UTF_8)
                    .replaceAll("(?i)USE shineconnoisseur;", "")
                    .replaceAll("(?i)CREATE TABLE (?!IF NOT EXISTS)", "CREATE TABLE IF NOT EXISTS ").trim() + ";";
            try (var connection = ds.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            }
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(PaymentOrderMapper.class);
        config.addMapper(UserVipMapper.class);
        config.addMapper(PaymentRefundMapper.class);
        config.addMapper(VipProductMapper.class);
        config.addInterceptor(faults);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var sessions = new SqlSessionTemplate(factory.getObject());
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class, PaymentRefundServiceImpl.class, PaymentFulfillmentServiceImpl.class,
                PaymentOrderStateServiceImpl.class, PaymentRefundProcessingServiceImpl.class,
                PaymentOrderReconciliationServiceImpl.class, PaymentOrderServiceImpl.class);
        context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(ds));
        context.registerBean(PaymentOrderMapper.class, () -> sessions.getMapper(PaymentOrderMapper.class));
        context.registerBean(UserVipMapper.class, () -> sessions.getMapper(UserVipMapper.class));
        context.registerBean(PaymentRefundMapper.class, () -> sessions.getMapper(PaymentRefundMapper.class));
        context.registerBean(VipProductMapper.class, () -> sessions.getMapper(VipProductMapper.class));
        context.registerBean(PaymentOrderCloseService.class, () -> mock(PaymentOrderCloseService.class));
        wechat = mock(WechatPayService.class);
        alipay = mock(AlipayService.class);
        context.registerBean(WechatPayService.class, () -> wechat);
        context.registerBean(AlipayService.class, () -> alipay);
        context.refresh();
        refunds = context.getBean(PaymentRefundService.class);
        fulfillment = context.getBean(PaymentFulfillmentService.class);
        states = context.getBean(PaymentOrderStateService.class);
        processing = context.getBean(PaymentRefundProcessingService.class);
        refundMapper = context.getBean(PaymentRefundMapper.class);
        orders = context.getBean(PaymentOrderMapper.class);
    }

    @BeforeEach void fixtures() {
        reset(wechat, alipay);
        faults.failGrant.set(false);
        faults.failRevoke.set(false);
        String key = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO user(username,password,phone) VALUES (?, 'test', ?)", key, key.substring(0, 20));
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, key);
        jdbc.update("INSERT INTO vip_product(name,duration_days,price) VALUES (?,30,19.90)", key);
        productId = jdbc.queryForObject("SELECT id FROM vip_product WHERE name=?", Long.class, key);
        baseline = LocalDateTime.now().plusDays(60).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO user_vip(user_id,start_time,expire_time) VALUES (?,?,?)",
                userId, baseline.minusDays(90), baseline);
    }

    @AfterAll void close() { if (context != null) context.close(); }

    private String newOrder() {
        String no = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO payment_order(order_no,request_id,user_id,product_id,duration_days,amount,payment_method,status,expire_time) VALUES (?,?,?,?,30,19.90,2,5,?)",
                no, no, userId, productId, LocalDateTime.now().plusMinutes(15));
        return no;
    }

    private String paidOrder() {
        String no = newOrder();
        pay(no);
        return no;
    }

    private void pay(String no) { fulfillment.fulfill(no, 2, "wx-" + no, new BigDecimal("19.90")); }
    private Long orderId(String no) { return jdbc.queryForObject("SELECT id FROM payment_order WHERE order_no=?", Long.class, no); }
    private int status(String no) { return jdbc.queryForObject("SELECT status FROM payment_order WHERE order_no=?", Integer.class, no); }
    private LocalDateTime expiry() {
        return jdbc.queryForObject("SELECT expire_time FROM user_vip WHERE user_id=?", LocalDateTime.class, userId);
    }

    @Test void acceptedRefundRetriesIdempotentlyAndDebitsOnlyOnce() {
        String no = paidOrder();
        PaymentRefund refund = refunds.request(userId, no);
        assertEquals(ORDER_STATUS_REFUNDING, status(no));
        assertEquals(baseline.plusDays(30), expiry());
        jdbc.update("UPDATE payment_order SET pay_time = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE order_no=?", no);
        assertEquals(refund.getId(), refunds.request(userId, no).getId()); // 已受理不再检查期限
        when(wechat.queryRefund(refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.FAILED);
        when(wechat.refundOrder(no, new BigDecimal("19.90"), refund.getRefundNo()))
                .thenReturn(com.lzh.enums.PaymentRefundResult.PROCESSING);
        processing.process(refund.getId());
        assertEquals(ORDER_STATUS_REFUNDING, status(no));
        assertEquals(baseline.plusDays(30), expiry());
        when(wechat.queryRefund(refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.SUCCESS);
        processing.process(refund.getId());
        processing.process(refund.getId());
        refunds.complete(refund.getId());
        pay(no); // 退款后的迟到重复回调不能重新发权益
        assertEquals(ORDER_STATUS_REFUND, status(no));
        assertEquals(baseline, expiry());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_refund WHERE order_id=?", Integer.class, orderId(no)));
        assertNotNull(refundMapper.selectById(refund.getId()).getRightsRevokedAt());
        verify(wechat, times(1)).refundOrder(no, new BigDecimal("19.90"), refund.getRefundNo());
    }

    @Test void onlyLatestPaidOrderAndNoBackwardRefundAfterSuccess() {
        String first = paidOrder();
        String second = paidOrder();
        newOrder(); // 未支付订单不影响资格
        assertThrows(BusinessException.class, () -> refunds.request(userId, first));
        assertThrows(BusinessException.class, () -> refunds.request(userId + 999999, second));
        var refund = refunds.request(userId, second);
        refunds.complete(refund.getId());
        assertThrows(BusinessException.class, () -> refunds.request(userId, first));
        assertEquals(baseline.plusDays(30), expiry());
    }

    @Test void expiredRequestDoesNotChangeOrderOrVip() {
        String no = paidOrder();
        jdbc.update("UPDATE payment_order SET pay_time=DATE_SUB(NOW(), INTERVAL 25 HOUR) WHERE order_no=?", no);
        assertThrows(BusinessException.class, () -> refunds.request(userId, no));
        assertEquals(ORDER_STATUS_SUCCESS, status(no));
        assertEquals(baseline.plusDays(30), expiry());
        assertNull(refundMapper.findByOrder(orderId(no)));
    }

    @Test void inFlightPaymentDeferredUntilRefundCompletes() {
        String no = paidOrder();
        String next = newOrder();
        var refund = refunds.request(userId, no);
        pay(next);
        assertEquals(ORDER_STATUS_PAID_PENDING, status(next));
        assertEquals(baseline.plusDays(30), expiry());
        String unstarted = newOrder();
        jdbc.update("UPDATE payment_order SET status=0 WHERE order_no=?", unstarted);
        assertFalse(states.transition(orderId(unstarted), ORDER_STATUS_PAYING, ORDER_STATUS_INITIATING));
        // 对账也不得把用户退款误当成自动补偿退款。
        assertEquals(PaymentOrderReconciliationService.Outcome.UNKNOWN,
                context.getBean(PaymentOrderReconciliationService.class).reconcile(orders.selectById(orderId(no))));
        verifyNoInteractions(wechat);
        refunds.complete(refund.getId());
        assertEquals(PaymentOrderReconciliationService.Outcome.FULFILLED,
                context.getBean(PaymentOrderReconciliationService.class).reconcile(orders.selectById(orderId(next))));
        assertEquals(ORDER_STATUS_SUCCESS, status(next));
        assertEquals(baseline.plusDays(30), expiry());
    }

    @Test void actualGrantFailureRollsBackOrderAndVip() {
        String no = newOrder();
        faults.failGrant.set(true);
        assertThrows(RuntimeException.class, () -> pay(no));
        assertEquals(ORDER_STATUS_WAIT_PAY, status(no));
        assertNull(jdbc.queryForObject("SELECT transaction_id FROM payment_order WHERE order_no=?", String.class, no));
        assertNull(jdbc.queryForObject("SELECT pay_time FROM payment_order WHERE order_no=?", LocalDateTime.class, no));
        assertEquals(baseline, expiry());
        pay(no);
        assertEquals(baseline.plusDays(30), expiry());
    }

    @Test void channelSuccessButLocalFailureRollsBackAndCanRecover() {
        String no = paidOrder();
        var refund = refunds.request(userId, no);
        when(wechat.queryRefund(refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.SUCCESS);
        faults.failRevoke.set(true);
        processing.process(refund.getId());
        assertEquals(ORDER_STATUS_REFUNDING, status(no));
        assertEquals("PROCESSING", refundMapper.selectById(refund.getId()).getStatus());
        assertEquals(baseline.plusDays(30), expiry());
        processing.process(refund.getId());
        assertEquals(ORDER_STATUS_REFUND, status(no));
        assertEquals(baseline, expiry());
        verify(wechat, never()).refundOrder(anyString(), any(), anyString());
    }

    @Test void concurrentRequestsAndCompletionAreIdempotent() throws Exception {
        String no = paidOrder();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CyclicBarrier(2);
            Callable<PaymentRefund> apply = () -> { gate.await(5, TimeUnit.SECONDS); return refunds.request(userId, no); };
            var a = pool.submit(apply);
            var b = pool.submit(apply);
            Long id = a.get(10, TimeUnit.SECONDS).getId();
            assertEquals(id, b.get(10, TimeUnit.SECONDS).getId());
            var finish = new CyclicBarrier(2);
            Callable<Void> complete = () -> { finish.await(5, TimeUnit.SECONDS); refunds.complete(id); return null; };
            var c = pool.submit(complete);
            var d = pool.submit(complete);
            c.get(10, TimeUnit.SECONDS); d.get(10, TimeUnit.SECONDS);
        }
        assertEquals(ORDER_STATUS_REFUND, status(no));
        assertEquals(baseline, expiry());
    }

    @Test void pendingRefundBlocksCreatingOrdersAndHistoryIsOwnerScoped() {
        String no = paidOrder();
        refunds.request(userId, no);
        var user = new com.lzh.dto.UserDTO();
        user.setId(userId);
        com.lzh.utils.UserHolder.setUser(user);
        try {
            assertFalse(context.getBean(IPaymentOrderService.class)
                    .createOrder(productId, 2, UUID.randomUUID().toString()).getSuccess());
        } finally { com.lzh.utils.UserHolder.removeUser(); }
        assertEquals(1, refunds.recentOrders(userId).size());
        assertFalse(refunds.recentOrders(userId).getFirst().isRefundEligible());
        assertTrue(refunds.recentOrders(userId + 999999).isEmpty());
    }

    @Test void firstPurchaseRefundLeavesNoActiveVip() {
        jdbc.update("DELETE FROM user_vip WHERE user_id=?", userId);
        String no = paidOrder();
        var refund = refunds.request(userId, no);
        refunds.complete(refund.getId());
        assertFalse(expiry().isAfter(LocalDateTime.now()));
        assertEquals(ORDER_STATUS_REFUND, status(no));
    }

    @Test void refundCommitWinsAgainstInFlightPayment() throws Exception {
        raceRefundAndPayment(true);
    }

    @Test void newerPaymentCommitWinsAgainstOlderRefundRequest() throws Exception {
        raceRefundAndPayment(false);
    }

    private void raceRefundAndPayment(boolean refundFirst) throws Exception {
        String old = paidOrder();
        String next = newOrder();
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contenderStarted = new CountDownLatch(1);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
                if (refundFirst) refunds.request(userId, old); else pay(next);
                held.countDown(); // 真实SQL已经执行，但用户行锁尚未随提交释放
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(held.await(5, TimeUnit.SECONDS));
            Future<?> contender = pool.submit(() -> {
                contenderStarted.countDown();
                if (refundFirst) pay(next);
                else assertThrows(BusinessException.class, () -> refunds.request(userId, old));
            });
            try {
                assertTrue(contenderStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> contender.get(200, TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
            contender.get(10, TimeUnit.SECONDS);
        }
        if (refundFirst) {
            assertEquals(ORDER_STATUS_REFUNDING, status(old));
            assertEquals(ORDER_STATUS_PAID_PENDING, status(next));
            assertEquals(baseline.plusDays(30), expiry());
        } else {
            assertEquals(ORDER_STATUS_SUCCESS, status(old));
            assertEquals(ORDER_STATUS_SUCCESS, status(next));
            assertNull(refundMapper.findByOrder(orderId(old)));
            assertEquals(baseline.plusDays(60), expiry());
        }
    }
}
