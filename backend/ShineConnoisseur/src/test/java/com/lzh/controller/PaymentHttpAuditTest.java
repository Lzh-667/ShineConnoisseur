package com.lzh.controller;

import com.lzh.common.Result;
import com.lzh.config.WebExceptionAdvice;
import com.lzh.service.*;
import com.lzh.utils.LoginInterceptor;
import com.lzh.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 仅测试 Servlet 与控制器边界：使用真实登录拦截器和模拟服务，不连接 Redis，也不启动真实 HTTP 服务器。 */
class PaymentHttpAuditTest {
    private MockMvc mvc;
    private IPaymentOrderService orders;
    private PaymentRefundService refunds;
    private AlipayService alipay;
    private WechatPayService wechat;

    @BeforeEach void setup() {
        UserHolder.removeUser();
        orders = mock(IPaymentOrderService.class);
        refunds = mock(PaymentRefundService.class);
        alipay = mock(AlipayService.class);
        wechat = mock(WechatPayService.class);
        var controller = new PaymentOrderController();
        ReflectionTestUtils.setField(controller, "paymentOrderService", orders);
        ReflectionTestUtils.setField(controller, "paymentRefundService", refunds);
        ReflectionTestUtils.setField(controller, "alipayService", alipay);
        ReflectionTestUtils.setField(controller, "wechatPayService", wechat);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new WebExceptionAdvice())
                .addMappedInterceptors(new String[]{"/payments/orders/**", "/payments/refunds/**",
                        "/payments/show", "/payments/delete", "/payments/alipay", "/payments/wechat"}, new LoginInterceptor())
                .build();
    }
    @AfterEach void clear() { UserHolder.removeUser(); }
    private void login() { var user = new com.lzh.dto.UserDTO(); user.setId(7L); UserHolder.setUser(user); }

    @Test void unauthenticatedBusinessEndpointsAreRejected() throws Exception {
        mvc.perform(post("/payments/orders").param("productId","1").param("paymentMethod","2").header("Idempotency-Key","key")).andExpect(status().isUnauthorized());
        mvc.perform(get("/payments/show").param("orderNo","x")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/payments/delete").param("orderNo","x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/payments/orders/recent")).andExpect(status().isUnauthorized());
        mvc.perform(get("/payments/refunds/quota")).andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/orders/x/refund")).andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/alipay").param("orderNo","x")).andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/wechat").param("orderNo","x")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders, refunds, alipay, wechat);
    }
    @Test void missingIdempotencyHeaderDoesNotCreateOrder() throws Exception {
        login();
        mvc.perform(post("/payments/orders").param("productId","1").param("paymentMethod","2"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }
    @Test void invalidNumericProductDoesNotCreateOrder() throws Exception {
        login();
        mvc.perform(post("/payments/orders").param("productId","abc").param("paymentMethod","2").header("Idempotency-Key","key"))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(orders);
    }
    @Test void refundUsesAuthenticatedUserAndNotSubmittedUserOrAmount() throws Exception {
        login();
        var refund = new com.lzh.po.PaymentRefund(); refund.setRefundNo("URx"); refund.setStatus("PROCESSING");
        when(refunds.request(7L,"x")).thenReturn(refund);
        mvc.perform(post("/payments/orders/x/refund").param("userId","999").param("amount","99999"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.status").value("PROCESSING"));
        verify(refunds).request(7L,"x");
    }
    @Test void missingWechatSignatureHeadersRejectBeforeService() throws Exception {
        mvc.perform(post("/payments/wechat/notify").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(wechat);
    }
    @Test void alipayHandlerFailureRequestsRetry() throws Exception {
        when(alipay.notify(anyMap())).thenThrow(new IllegalStateException("injected"));
        mvc.perform(post("/payments/alipay/notify").param("out_trade_no","x"))
                .andExpect(status().isInternalServerError()).andExpect(content().string("failure"));
    }
    @Test void alipayRejectedSignatureReturnsFailureNotSuccess() throws Exception {
        when(alipay.notify(anyMap())).thenReturn(false);
        mvc.perform(post("/payments/alipay/notify").param("out_trade_no","x"))
                .andExpect(content().string("failure"));
    }
}
