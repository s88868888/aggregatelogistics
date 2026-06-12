package net.kdks.autoconfigure;

import lombok.Data;
import net.kdks.config.BaishiConfig;
import net.kdks.config.JingdongConfig;
import net.kdks.config.JituConfig;
import net.kdks.config.ShentongConfig;
import net.kdks.config.ShunfengConfig;
import net.kdks.config.YuantongConfig;
import net.kdks.config.YundaConfig;
import net.kdks.config.ZhongtongConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 物流聚合配置属性.
 *
 * <pre>
 * aggregate-logistics:
 *   shunfeng:
 *     partner-id: xxx
 *     request-id: xxx
 *     check-word: xxx
 *     is-product: 1
 *   zhongtong:
 *     app-key: xxx
 *     secret-key: xxx
 *     is-product: 1
 *   yuantong:
 *     appkey: xxx
 *     secret-key: xxx
 *     user-id: xxx
 *     is-product: 1
 *   shentong:
 *     appkey: xxx
 *     secret-key: xxx
 *     is-product: 1
 *   baishi:
 *     partner-id: xxx
 *     secret-key: xxx
 *     is-product: 1
 *   jitu:
 *     api-account: xxx
 *     private-key: xxx
 *     is-product: 1
 *   yunda:
 *     app-key: xxx
 *     app-secret: xxx
 *     is-product: 1
 *   jingdong:
 *     app-key: xxx
 *     app-secret: xxx
 *     access-token: xxx
 *     customer-code: xxx
 *     is-product: 1
 * </pre>
 *
 * @author aggregatelogistics
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "aggregate-logistics")
public class LogisticsProperties {

    /**
     * 是否启用自动装配.
     */
    private boolean enabled = true;

    /**
     * 顺丰配置.
     */
    @NestedConfigurationProperty
    private ShunfengConfig shunfeng;

    /**
     * 中通配置.
     */
    @NestedConfigurationProperty
    private ZhongtongConfig zhongtong;

    /**
     * 圆通配置.
     */
    @NestedConfigurationProperty
    private YuantongConfig yuantong;

    /**
     * 申通配置.
     */
    @NestedConfigurationProperty
    private ShentongConfig shentong;

    /**
     * 百世配置.
     */
    @NestedConfigurationProperty
    private BaishiConfig baishi;

    /**
     * 极兔配置.
     */
    @NestedConfigurationProperty
    private JituConfig jitu;

    /**
     * 韵达配置.
     */
    @NestedConfigurationProperty
    private YundaConfig yunda;

    /**
     * 京东配置.
     */
    @NestedConfigurationProperty
    private JingdongConfig jingdong;
}
