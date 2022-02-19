package cn.zack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 注入restTemplate配置
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 5秒连接超时
        factory.setConnectTimeout(50000);
        // 2秒读取超时
        factory.setReadTimeout(20000);
        return new RestTemplate(factory);
    }

}
