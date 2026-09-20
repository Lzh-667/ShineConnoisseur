package com.lzh.controller;

import com.lzh.service.AlipayService;
import com.lzh.service.IPaymentOrderService;
import com.lzh.service.WechatPayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderControllerTest {

    @Mock
    private IPaymentOrderService paymentOrderService;
    @Mock
    private AlipayService alipayService;
    @Mock
    private WechatPayService wechatPayService;
    @InjectMocks
    private PaymentOrderController controller;

    @Test
    void alipayNotify_success_returnsExpectedBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("out_trade_no", "order-1");
        when(alipayService.notify(anyMap())).thenReturn(true);

        ResponseEntity<String> response = controller.alipayNotify(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", response.getBody());
    }

    @Test
    void wechatNotify_success_returns204() {
        when(wechatPayService.notify("body", "signature", "timestamp", "nonce", "serial"))
                .thenReturn(true);

        ResponseEntity<String> response = controller.wechatNotify(
                "body", "signature", "timestamp", "nonce", "serial"
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void wechatNotify_failure_returns500() {
        when(wechatPayService.notify("body", "signature", "timestamp", "nonce", "serial"))
                .thenReturn(false);

        ResponseEntity<String> response = controller.wechatNotify(
                "body", "signature", "timestamp", "nonce", "serial"
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("{\"code\":\"FAIL\",\"message\":\"处理失败\"}", response.getBody());
    }
}
