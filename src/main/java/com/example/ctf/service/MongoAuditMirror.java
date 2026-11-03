package com.example.ctf.service;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.util.Date;

/**
 * 审计日志 Mongo 镜像：事务提交后将审计事件同步写入 op_logs 集合，便于检索与长期留存。
 * 默认关闭，通过 MONGO_ENABLED=true 开启；写入失败仅告警，不影响主流程。
 */
@Component
@ConditionalOnProperty(name = "app.mongo.enabled", havingValue = "true")
public class MongoAuditMirror {

    private static final Logger log = LoggerFactory.getLogger(MongoAuditMirror.class);
    private static final String COLLECTION = "op_logs";

    private final MongoTemplate mongoTemplate;

    public MongoAuditMirror(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditRecorded(AuditRecordedEvent event) {
        try {
            Document doc = new Document()
                    .append("action", event.action())
                    .append("actorId", event.actorId())
                    .append("actorName", event.actorName())
                    .append("actorRole", event.actorRole())
                    .append("target", event.target())
                    .append("detail", event.detail())
                    .append("ip", event.ip())
                    .append("createdAt", event.createdAt() == null ? null
                            : Date.from(event.createdAt().atZone(ZoneId.systemDefault()).toInstant()));
            mongoTemplate.getCollection(COLLECTION).insertOne(doc);
        } catch (Exception ex) {
            log.warn("审计日志写入 MongoDB 失败 action={}: {}", event.action(), ex.getMessage());
        }
    }
}
