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
    private final java.time.Clock refundClock = mock(java.time.Clock.class);
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
        // 在创建映射器元数据和插入处理器之前，注册生产环境使用的自动填充处理器。
        var globalConfig = new com.baomidou.mybatisplus.core.config.GlobalConfig()
                .setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig())
                .setMetaObjectHandler(new com.lzh.config.MyMetaObjectHandler());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config, globalConfig);
        config.addMapper(PaymentOrderMapper.class);
        config.addMapper(UserVipMapper.class);
        config.addMapper(PaymentRefundMapper.class);
        config.addMapper(VipProductMapper.class);
        config.addInterceptor(faults);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        factory.setGlobalConfig(globalConfig);
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
        context.registerBean(java.time.Clock.class, () -> refundClock);
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
        reset(refundClock);
        when(refundClock.instant()).thenAnswer(invocation -> java.time.Instant.now());
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
        seedRefund("SUCCESS", LocalDateTime.now().withDayOfYear(1).toLocalDate().atStartOfDay());
        seedRefund("SUCCESS", LocalDateTime.now().withDayOfYear(1).toLocalDate().atStartOfDay());
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
        assertEquals(3, refunds.quota(userId).used());
        assertEquals(0, refunds.quota(userId).remaining());
    }

    private void seedRefund(String status, LocalDateTime requestedAt) {
        String no = newOrder();
        jdbc.update("INSERT INTO payment_refund(order_id,user_id,order_no,refund_no,amount,duration_days,status,entitlement_expire_before,entitlement_expire_after,requested_at,next_retry_at) VALUES (?,?,?,?,19.90,30,?,?,?,?,?)",
                orderId(no), userId, no, "UR" + no, status, baseline.plusDays(30), baseline, requestedAt, requestedAt);
    }

    @Test void threeRefundsAllowedFourthRejectedAndReplayStillWorks() {
        PaymentRefund last = null;
        for (int i = 0; i < 3; i++) {
            String no = paidOrder();
            last = refunds.request(userId, no);
            assertEquals(i + 1, refunds.quota(userId).used()); // 处理中已经占用
            refunds.complete(last.getId());
        }
        assertEquals(last.getId(), refunds.request(userId, last.getOrderNo()).getId());
        String fourth = paidOrder();
        assertTrue(assertThrows(BusinessException.class, () -> refunds.request(userId, fourth))
                .getMessage().contains("3次"));
        assertNull(refundMapper.findByOrder(orderId(fourth)));
        assertEquals(ORDER_STATUS_SUCCESS, status(fourth));
        assertEquals(baseline.plusDays(30), expiry());
        assertFalse(refunds.recentOrders(userId).stream()
                .filter(o -> fourth.equals(o.getOrderNo())).findFirst().orElseThrow().isRefundEligible());
    }

    @Test void beijingYearBoundariesAndUnknownResultsKeepQuota() {
        // UTC还是12月31日，但北京时间已经进入2031年；不依赖测试机默认时区。
        when(refundClock.instant()).thenReturn(java.time.Instant.parse("2030-12-31T16:00:00Z"));
        LocalDateTime start = LocalDateTime.of(2031, 1, 1, 0, 0);
        seedRefund("SUCCESS", start.minusNanos(1000));
        seedRefund("PROCESSING", start);
        seedRefund("SUCCESS", start.plusMonths(1));
        seedRefund("SUCCESS", start.plusYears(1)); // 右边界不计入
        assertEquals(2031, refunds.quota(userId).year());
        assertEquals(2, refunds.quota(userId).used());
        assertEquals(1, refunds.quota(userId).remaining());
        Long pending = refundMapper.pendingId(userId);
        refundMapper.recordRetry(pending, "网络超时，退款结果未知", start.plusMinutes(1));
        assertEquals(2, refunds.quota(userId).used());
        // 只有已确认终止的记录才能释放；系统没有超时自动终止路径。
        jdbc.update("UPDATE payment_refund SET status='CANCELLED' WHERE id=?", pending);
        assertEquals(1, refunds.quota(userId).used());
    }

    @Test void crossYearCompletionBelongsToApplicationYearAndAutomaticRefundDoesNotCount() {
        String no = paidOrder();
        LocalDateTime dec31 = LocalDateTime.of(2030, 12, 31, 23, 59, 59);
        jdbc.update("UPDATE payment_order SET pay_time=? WHERE order_no=?", dec31.minusHours(1), no);
        when(refundClock.instant()).thenReturn(dec31.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        var refund = refunds.request(userId, no);
        assertEquals(1, refunds.quota(userId).used());
        when(refundClock.instant()).thenReturn(java.time.Instant.parse("2030-12-31T16:00:01Z"));
        refunds.complete(refund.getId());
        assertEquals(0, refunds.quota(userId).used());
        assertEquals(3, refunds.quota(userId).remaining());
        assertEquals(1, refundMapper.annualUsage(userId,
                LocalDateTime.of(2030, 1, 1, 0, 0), LocalDateTime.of(2031, 1, 1, 0, 0)));
        String automatic = newOrder();
        jdbc.update("UPDATE payment_order SET status=3 WHERE order_no=?", automatic);
        assertEquals(0, refunds.quota(userId).used()); // 自动补偿没有用户退款记录
        assertEquals(0, refunds.quota(userId + 999999).used());
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

    @Test void orderCreationIsIdempotentAndSnapshotSurvivesProductChanges() {
        var user = new com.lzh.dto.UserDTO();
        user.setId(userId);
        com.lzh.utils.UserHolder.setUser(user);
        try {
            var service = context.getBean(IPaymentOrderService.class);
            String key = UUID.randomUUID().toString();
            var first = service.createOrder(productId, 2, key);
            assertTrue(first.getSuccess());
            var original = (com.lzh.vo.PaymentOrderVO) first.getData();
            jdbc.update("UPDATE vip_product SET price=99.90,duration_days=365,status=0 WHERE id=?", productId);
            var replay = service.createOrder(productId, 2, key);
            assertTrue(replay.getSuccess());
            assertEquals(original.getOrderNo(), ((com.lzh.vo.PaymentOrderVO) replay.getData()).getOrderNo());
            assertFalse(service.createOrder(productId, 1, key).getSuccess());
            assertFalse(service.createOrder(productId, 99, UUID.randomUUID().toString()).getSuccess());
            assertFalse(service.createOrder(productId, 2, " ").getSuccess());
            assertFalse(service.createOrder(productId, 2, "x".repeat(65)).getSuccess());
            pay(original.getOrderNo());
            assertEquals(baseline.plusDays(30), expiry());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_order WHERE user_id=?", Integer.class, userId));
        } finally { com.lzh.utils.UserHolder.removeUser(); }
    }

    @Test void showAndCancelAreOwnerScoped() {
        String no = newOrder();
        var user = new com.lzh.dto.UserDTO();
        user.setId(userId + 999999);
        com.lzh.utils.UserHolder.setUser(user);
        try {
            var service = context.getBean(IPaymentOrderService.class);
            assertFalse(service.showOrder(no).getSuccess());
            assertFalse(service.deleteOrder(no).getSuccess());
            assertEquals(ORDER_STATUS_WAIT_PAY, status(no));
        } finally { com.lzh.utils.UserHolder.removeUser(); }
    }

    @Test void wrongChannelAmountAndTransactionCannotGrantAgain() {
        String no = newOrder();
        assertThrows(BusinessException.class, () -> fulfillment.fulfill(no, 1, "wrong-channel", new BigDecimal("19.90")));
        assertThrows(BusinessException.class, () -> fulfillment.fulfill(no, 2, "wrong-amount", new BigDecimal("0.01")));
        assertEquals(ORDER_STATUS_WAIT_PAY, status(no));
        assertEquals(baseline, expiry());
        pay(no);
        pay(no);
        assertThrows(BusinessException.class, () -> fulfillment.fulfill(no, 2, "different-tx", new BigDecimal("19.90")));
        assertEquals(baseline.plusDays(30), expiry());
    }

    @Test void transactionIdCannotBeReusedAcrossOrders() {
        String first = paidOrder();
        String next = newOrder();
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> fulfillment.fulfill(next, 2, "wx-" + first, new BigDecimal("19.90")));
        assertEquals(ORDER_STATUS_WAIT_PAY, status(next));
        assertEquals(baseline.plusDays(30), expiry());
    }

    @Test void exact24HourBoundaryAllowedButOneMicrosecondLaterRejected() {
        String no = paidOrder();
        LocalDateTime paid = LocalDateTime.of(2031, 5, 1, 12, 0);
        jdbc.update("UPDATE payment_order SET pay_time=? WHERE order_no=?", paid, no);
        when(refundClock.instant()).thenReturn(paid.plusHours(24).plusNanos(1000)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        assertThrows(BusinessException.class, () -> refunds.request(userId, no));
        assertNull(refundMapper.findByOrder(orderId(no)));
        when(refundClock.instant()).thenReturn(paid.plusHours(24)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
        assertNotNull(refunds.request(userId, no));
    }

    @Test void concurrentDuplicateCallbacksGrantExactlyOnce() throws Exception {
        String no = newOrder();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var gate = new CyclicBarrier(8);
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                gate.await(10, TimeUnit.SECONDS); pay(no); return null;
            }));
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertEquals(ORDER_STATUS_SUCCESS, status(no));
        assertEquals(baseline.plusDays(30), expiry());
    }

    @Test void paymentWinsAgainstCloseInRealMysql() throws Exception { raceCloseAndPayment(true); }
    @Test void closeWinsThenLatePaymentStillGrantsExactlyOnce() throws Exception { raceCloseAndPayment(false); }

    private void raceCloseAndPayment(boolean paymentFirst) throws Exception {
        String no = newOrder();
        jdbc.update("UPDATE payment_order SET status=? WHERE order_no=?", ORDER_STATUS_CLOSING, no);
        Long id = orderId(no);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contenderStarted = new CountDownLatch(1);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> tx.executeWithoutResult(s -> {
                if (paymentFirst) pay(no);
                else assertTrue(states.transition(id, ORDER_STATUS_CLOSING, ORDER_STATUS_CLOSE));
                held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout"); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }));
            assertTrue(held.await(10, TimeUnit.SECONDS));
            var contender = pool.submit(() -> {
                contenderStarted.countDown();
                if (paymentFirst) assertFalse(states.transition(id, ORDER_STATUS_CLOSING, ORDER_STATUS_CLOSE));
                else pay(no);
            });
            try {
                assertTrue(contenderStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> contender.get(200, TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            holder.get(15, TimeUnit.SECONDS);
            contender.get(15, TimeUnit.SECONDS);
        }
        pay(no);
        assertEquals(ORDER_STATUS_SUCCESS, status(no));
        assertEquals("wx-" + no, jdbc.queryForObject("SELECT transaction_id FROM payment_order WHERE order_no=?", String.class, no));
        assertEquals(baseline.plusDays(30), expiry());
    }

    /** 现状刻画：支付渠道永久失败时，目前没有终止状态或人工处理状态。 */
    @Test void permanentRefundFailureKeepsQuotaAndBlocksPurchase() {
        String no = paidOrder();
        var refund = refunds.request(userId, no);
        when(wechat.queryRefund(refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.FAILED);
        when(wechat.refundOrder(no, refund.getAmount(), refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.FAILED);
        for (int i = 0; i < 4; i++) processing.process(refund.getId());
        assertEquals("PROCESSING", refundMapper.selectById(refund.getId()).getStatus());
        assertEquals(1, refunds.quota(userId).used());
        assertEquals(1, refundMapper.pendingCount(userId));
        assertEquals(ORDER_STATUS_REFUNDING, status(no));
        assertEquals(baseline.plusDays(30), expiry());
    }

    /** 最近已支付订单必须保留退款入口，不被后创建的未支付订单遮蔽。 */
    @Test void recentTwentyRetainsTheOnlyRefundEligibleOrder() {
        String paid = paidOrder();
        for (int i = 0; i < 20; i++) newOrder();
        var recent = refunds.recentOrders(userId);
        assertEquals(21, recent.size());
        assertTrue(recent.stream().anyMatch(o -> paid.equals(o.getOrderNo()) && o.isRefundEligible()));
        assertNotNull(refunds.request(userId, paid));
    }

    @Test void signedAlipayCallbackRejectsWrongMerchantAndTampering() throws Exception {
        String no = newOrder();
        jdbc.update("UPDATE payment_order SET payment_method=1 WHERE order_no=?", no);
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var config = new com.lzh.config.AlipayConfig();
        config.setAppId("audit-app"); config.setSellerId("audit-seller");
        config.setAlipayPublicKey(java.util.Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        var service = new AlipayServiceImpl(config, orders, fulfillment, states, refundMapper);
        var params = new java.util.HashMap<String, String>();
        params.put("out_trade_no", no); params.put("trade_no", "ali-" + no);
        params.put("total_amount", "19.90"); params.put("trade_status", "TRADE_SUCCESS");
        params.put("app_id", "audit-app"); params.put("seller_id", "wrong-seller");
        signCallback(params, keys);
        assertFalse(service.notify(new java.util.HashMap<>(params)));
        assertEquals(ORDER_STATUS_WAIT_PAY, status(no));
        params.put("seller_id", "audit-seller"); signCallback(params, keys);
        var tampered = new java.util.HashMap<>(params);
        tampered.put("total_amount", "0.01");
        assertFalse(service.notify(tampered));
        assertTrue(service.notify(new java.util.HashMap<>(params)));
        assertTrue(service.notify(new java.util.HashMap<>(params)));
        assertEquals(ORDER_STATUS_SUCCESS, status(no));
        assertEquals(baseline.plusDays(30), expiry());
    }

    /** 现状刻画：忽略渠道已签名的支付时间，会重新开启 24 小时退款窗口。 */
    @Test void delayedSignedCallbackCurrentlyAllowsRefundBeyondActualPayment24Hours() throws Exception {
        String no = newOrder();
        jdbc.update("UPDATE payment_order SET payment_method=1 WHERE order_no=?", no);
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var config = new com.lzh.config.AlipayConfig();
        config.setAppId("audit-app"); config.setSellerId("audit-seller");
        config.setAlipayPublicKey(java.util.Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        var service = new AlipayServiceImpl(config, orders, fulfillment, states, refundMapper);
        LocalDateTime actualPaid = LocalDateTime.now().minusHours(25).truncatedTo(ChronoUnit.SECONDS);
        var params = new java.util.HashMap<String, String>();
        params.put("out_trade_no", no); params.put("trade_no", "ali-" + no);
        params.put("total_amount", "19.90"); params.put("trade_status", "TRADE_SUCCESS");
        params.put("app_id", "audit-app"); params.put("seller_id", "audit-seller");
        params.put("gmt_payment", actualPaid.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        signCallback(params, keys);
        assertTrue(service.notify(params));
        LocalDateTime stored = jdbc.queryForObject("SELECT pay_time FROM payment_order WHERE order_no=?", LocalDateTime.class, no);
        assertTrue(stored.isAfter(actualPaid.plusHours(24)));
        assertNotNull(refunds.request(userId, no));
    }

    private void signCallback(java.util.Map<String, String> params, java.security.KeyPair keys) throws Exception {
        params.remove("sign"); params.remove("sign_type");
        String content = new java.util.TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("&"));
        String signature = com.alipay.api.internal.util.AlipaySignature.rsaSign(content,
                java.util.Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()), "UTF-8", "RSA2");
        params.put("sign", signature); params.put("sign_type", "RSA2");
    }

    @Test void concurrentCreateWithSameKeyReturnsSingleOrder() throws Exception {
        var service = context.getBean(IPaymentOrderService.class);
        String key = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(6)) {
            var gate = new CyclicBarrier(6);
            var results = new java.util.ArrayList<Future<String>>();
            for (int i = 0; i < 6; i++) results.add(pool.submit(() -> {
                var user = new com.lzh.dto.UserDTO(); user.setId(userId);
                com.lzh.utils.UserHolder.setUser(user);
                try {
                    gate.await(10, TimeUnit.SECONDS);
                    var result = service.createOrder(productId, 2, key);
                    assertTrue(result.getSuccess());
                    return ((com.lzh.vo.PaymentOrderVO) result.getData()).getOrderNo();
                } finally { com.lzh.utils.UserHolder.removeUser(); }
            }));
            var numbers = new java.util.HashSet<String>();
            for (var result : results) numbers.add(result.get(20, TimeUnit.SECONDS));
            assertEquals(1, numbers.size());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_order WHERE user_id=? AND request_id=?", Integer.class, userId, key));
    }

    @Test void expiredVipRenewalStartsNowNotAtOldExpiry() {
        jdbc.update("UPDATE user_vip SET expire_time=? WHERE user_id=?", LocalDateTime.now().minusDays(10), userId);
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        paidOrder();
        assertFalse(expiry().isBefore(before.plusDays(30)));
        assertFalse(expiry().isAfter(LocalDateTime.now().plusDays(30)));
    }

    @Test void changedVipSnapshotDoesNotSilentlyOverwriteOtherRights() {
        String no = paidOrder();
        var refund = refunds.request(userId, no);
        jdbc.update("UPDATE user_vip SET expire_time=? WHERE user_id=?", baseline.plusDays(31), userId);
        assertThrows(BusinessException.class, () -> refunds.complete(refund.getId()));
        assertEquals(ORDER_STATUS_REFUNDING, status(no));
        assertEquals("PROCESSING", refundMapper.selectById(refund.getId()).getStatus());
        assertEquals(baseline.plusDays(31), expiry());
    }

    @Test void alipayUserRefundUsesUserRefundNumberAndRevokesOnce() {
        String no = newOrder();
        jdbc.update("UPDATE payment_order SET payment_method=1 WHERE order_no=?", no);
        fulfillment.fulfill(no, 1, "ali-" + no, new BigDecimal("19.90"));
        var refund = refunds.request(userId, no);
        when(alipay.queryRefund(no, refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.FAILED);
        when(alipay.refundOrder(no, refund.getAmount(), refund.getRefundNo())).thenReturn(com.lzh.enums.PaymentRefundResult.SUCCESS);
        processing.process(refund.getId());
        processing.process(refund.getId());
        verify(alipay, times(1)).refundOrder(no, refund.getAmount(), "UR" + no);
        verifyNoInteractions(wechat);
        assertEquals(ORDER_STATUS_REFUND, status(no));
        assertEquals(baseline, expiry());
    }

    @Test void closedScanUsesRealSqlCursorAndDoesNotExtendWindow() {
        LocalDateTime closedAt = LocalDateTime.now().minusMinutes(2).truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 101; i++) {
            String no = newOrder();
            jdbc.update("UPDATE payment_order SET status=?,update_time=? WHERE order_no=?", ORDER_STATUS_CLOSE, closedAt, no);
        }
        // 使用真实查询SQL，仅在测试边界追加用户范围，避免扫描其他用例的数据。
        var scopedMapper = mock(PaymentOrderMapper.class);
        when(scopedMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lzh.po.PaymentOrder> query = invocation.getArgument(0);
            return orders.selectList(query.eq(com.lzh.po.PaymentOrder::getUserId, userId));
        });
        var reconciliation = mock(PaymentOrderReconciliationService.class);
        when(reconciliation.reconcile(any())).thenReturn(PaymentOrderReconciliationService.Outcome.NOT_FOUND);
        var task = new com.lzh.task.PaymentOrderReconciliationTask(scopedMapper, reconciliation, states);
        task.reconcileOrders();
        verify(reconciliation, times(101)).reconcile(any());
        assertEquals(101, jdbc.queryForObject("SELECT COUNT(*) FROM payment_order WHERE user_id=? AND update_time=? AND status=?",
                Integer.class, userId, closedAt, ORDER_STATUS_CLOSE));
        jdbc.update("UPDATE payment_order SET update_time=? WHERE user_id=?", LocalDateTime.now().minusMinutes(11), userId);
        task.reconcileOrders();
        verify(reconciliation, times(101)).reconcile(any());
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
