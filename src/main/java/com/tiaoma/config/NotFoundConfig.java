package com.tiaoma.config;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * ให้หน้า 404 ของเว็บ (404.html) ถูกใช้แทนหน้า error ขาวๆ ของ Tomcat
 * เมื่อผู้ใช้พิมพ์ URL ผิด
 */
@Component
public class NotFoundConfig implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/404.html"));
    }
}
