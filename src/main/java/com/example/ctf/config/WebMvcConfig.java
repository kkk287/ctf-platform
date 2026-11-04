package com.example.ctf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 根路径与旧版原生页面入口重定向到 Vue 选手端。
 * <p>
 * 原生静态页面已下线，避免旧书签/文档链接 404：
 * {@code /}、{@code /index.html}、{@code /login.html} 统一跳转到 {@code /vue/player.html}。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/vue/player.html");
        registry.addRedirectViewController("/index.html", "/vue/player.html");
        registry.addRedirectViewController("/login.html", "/vue/player.html");
    }
}
