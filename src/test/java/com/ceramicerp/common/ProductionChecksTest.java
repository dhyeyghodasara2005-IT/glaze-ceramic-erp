package com.ceramicerp.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionChecksTest {

    @Test
    void realValuesPass() {
        assertThatCode(() -> ProductionChecks.check(Map.of("a", "something-real", "b", "another"))).doesNotThrowAnyException();
    }

    @Test
    void theTemplatesPlaceholderOrNothingIsRefusedAndAllAreNamed() {
        assertThatThrownBy(() -> ProductionChecks.check(Map.of("b.password", "CHANGE_ME", "a.secret", " ", "c.fine", "real")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Set real values in config/application-local.yml before running in production: a.secret, b.password");
    }

    @Test
    void theMessageNeverShowsAValue() {
        assertThatThrownBy(() -> ProductionChecks.check(Map.of("a.secret", "CHANGE_ME")))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("CHANGE_ME"));
    }
}
