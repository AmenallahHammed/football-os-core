package com.fos.sdk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final FosKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final FosSecurityContext securityContext;

    public AuditAspect(FosKafkaProducer kafkaProducer, ObjectMapper objectMapper, FosSecurityContext securityContext) {
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
        this.securityContext = securityContext;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed();
        try {
            String actorId = securityContext.actorId();
            var payload = objectMapper.createObjectNode()
                .put("action", audited.action())
                .put("resourceType", audited.resourceType())
                .put("actorId", actorId);

            kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(actorId)
                .payload(payload)
                .build());
        } catch (Exception e) {
            log.warn("Failed to emit audit signal for action={}: {}", audited.action(), e.getMessage());
            // Audit failure must never break the business operation
        }
        return result;
    }
}
