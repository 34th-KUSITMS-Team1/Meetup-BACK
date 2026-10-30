package com.chamabosal.global.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.chamabosal.global.config.ClockConfig;
import com.chamabosal.global.config.JpaAuditingConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, ClockConfig.class})
class BaseEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 저장_시_생성_수정_시각이_기록된다() {
        AuditedEntity entity = entityManager.persistFlushFind(new AuditedEntity());

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Entity
    static class AuditedEntity extends BaseEntity {

        @Id
        @GeneratedValue
        private Long id;
    }
}
