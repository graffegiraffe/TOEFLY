package ${package}.architecture;

import com.toefly.common.test.ToeflyArchitectureRules;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void followsToeflyArchitectureRules() {
        ToeflyArchitectureRules.check("${package}");
    }
}
