package com.saran.agent.tools;

import com.saran.agent.tools.impl.CalculatorTool;
import com.saran.agent.tools.impl.OrderStatusTool;
import com.saran.agent.tools.impl.WeatherTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolsTest {

    private final CalculatorTool calculator = new CalculatorTool();
    private final OrderStatusTool orderStatus = new OrderStatusTool();
    private final WeatherTool weather = new WeatherTool();

    @Test
    void calculatorPerformsBasicOperations() {
        assertThat(calculator.execute(Map.of("operation", "add", "a", 2, "b", 3))).isEqualTo("5");
        assertThat(calculator.execute(Map.of("operation", "multiply", "a", 19.99, "b", 3))).isEqualTo("59.97");
        assertThat(calculator.execute(Map.of("operation", "divide", "a", 10, "b", 4))).isEqualTo("2.5");
    }

    @Test
    void calculatorRejectsDivisionByZero() {
        assertThatThrownBy(() -> calculator.execute(Map.of("operation", "divide", "a", 1, "b", 0)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("zero");
    }

    @Test
    void orderStatusFindsKnownOrderCaseInsensitively() {
        assertThat(orderStatus.execute(Map.of("order_id", "a-1001"))).contains("FedEx");
    }

    @Test
    void orderStatusRejectsUnknownOrder() {
        assertThatThrownBy(() -> orderStatus.execute(Map.of("order_id", "B-9999")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("No order found");
    }

    @Test
    void weatherReturnsForecastForKnownCity() {
        assertThat(weather.execute(Map.of("city", "Austin"))).contains("Sunny");
    }

    @Test
    void registryRejectsDuplicateToolNames() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(calculator, new CalculatorTool())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void registryExposesDefinitionsForAllTools() {
        ToolRegistry registry = new ToolRegistry(List.of(calculator, orderStatus, weather));
        assertThat(registry.definitions()).hasSize(3);
        assertThat(registry.find("calculate")).isPresent();
        assertThat(registry.find("missing")).isEmpty();
    }
}
