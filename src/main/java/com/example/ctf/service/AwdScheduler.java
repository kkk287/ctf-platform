package com.example.ctf.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** AWD 定时推进：轮次到期自动下一轮/结算，并按 check 间隔执行探活。 */
@Component
public class AwdScheduler {

    private static final Logger log = LoggerFactory.getLogger(AwdScheduler.class);

    private final AwdService awdService;

    public AwdScheduler(AwdService awdService) {
        this.awdService = awdService;
    }

    @Scheduled(fixedDelayString = "${app.awd.tick-ms:5000}")
    public void tick() {
        try {
            awdService.tick(LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("AWD 定时推进异常: {}", ex.getMessage());
        }
    }
}
