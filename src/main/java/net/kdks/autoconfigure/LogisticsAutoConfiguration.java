package net.kdks.autoconfigure;

import net.kdks.config.ExpressConfig;
import net.kdks.handler.ExpressHandlers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 物流聚合自动装配.
 *
 * <p>Spring Boot 项目引入依赖后，只要 yml 中配置了 aggregate-logistics 即可自动注入 ExpressHandlers</p>
 *
 * @author aggregatelogistics
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(ExpressHandlers.class)
@ConditionalOnProperty(prefix = "aggregate-logistics", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogisticsProperties.class)
public class LogisticsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExpressConfig expressConfig(LogisticsProperties properties) {
        ExpressConfig config = new ExpressConfig();
        config.setShunfengConfig(properties.getShunfeng());
        config.setZhongtongConfig(properties.getZhongtong());
        config.setYuantongConfig(properties.getYuantong());
        config.setShentongConfig(properties.getShentong());
        config.setBaishiConfig(properties.getBaishi());
        config.setJituConfig(properties.getJitu());
        config.setYundaConfig(properties.getYunda());
        config.setJingdongConfig(properties.getJingdong());
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public ExpressHandlers expressHandlers(ExpressConfig expressConfig) {
        return new ExpressHandlers(expressConfig);
    }
}
