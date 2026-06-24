package com.example.ticket_system.main_business.pay.gateway;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付网关工厂
 * <p>
 * 根据配置 {@code pay.channel} 选择当前激活的支付网关。
 * 新增支付平台时只需：
 * <ol>
 *   <li>实现 {@link PaymentGateway} 接口</li>
 *   <li>注册为 Spring Bean（@Component）</li>
 *   <li>修改配置文件 {@code pay.channel=wechat} 即可切换</li>
 * </ol>
 */
@Component
public class PaymentGatewayFactory {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayFactory.class);

    /** 当前激活的支付渠道：mock / wechat / alipay */
    @Value("${pay.channel:mock}")
    private String activeChannel;

    @Autowired
    private List<PaymentGateway> gatewayList;

    /** channel → PaymentGateway 映射 */
    private final Map<String, PaymentGateway> gatewayMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (PaymentGateway gateway : gatewayList) {
            gatewayMap.put(gateway.getChannel(), gateway);
            log.info("注册支付网关：channel={}, class={}", gateway.getChannel(), gateway.getClass().getSimpleName());
        }
        log.info("当前激活支付渠道：{}", activeChannel);
    }

    /**
     * 获取当前激活的支付网关
     */
    public PaymentGateway getGateway() {
        PaymentGateway gateway = gatewayMap.get(activeChannel);
        if (gateway == null) {
            throw new IllegalStateException(
                    "未找到支付网关实现，channel=" + activeChannel
                            + "，可用渠道：" + gatewayMap.keySet());
        }
        return gateway;
    }

    /**
     * 按渠道获取指定支付网关
     */
    public PaymentGateway getGateway(String channel) {
        PaymentGateway gateway = gatewayMap.get(channel);
        if (gateway == null) {
            throw new IllegalArgumentException("不支持的支付渠道：" + channel);
        }
        return gateway;
    }

    /** 获取当前渠道名 */
    public String getActiveChannel() {
        return activeChannel;
    }
}