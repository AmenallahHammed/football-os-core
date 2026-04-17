package com.fos.workspace.config;

import io.mongock.driver.mongodb.springdata.v4.SpringDataMongoV4Driver;
import io.mongock.runner.springboot.MongockSpringboot;
import io.mongock.runner.springboot.base.MongockApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongockConfig {

    @Bean
    public MongockApplicationRunner mongockApplicationRunner(MongoTemplate mongoTemplate,
                                                             ApplicationContext springContext) {
        return MongockSpringboot.builder()
                .setDriver(SpringDataMongoV4Driver.withDefaultLock(mongoTemplate))
                .addMigrationScanPackage("com.fos.workspace.db.migration")
                .setSpringContext(springContext)
                .buildApplicationRunner();
    }
}
