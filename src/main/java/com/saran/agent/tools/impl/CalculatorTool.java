package com.saran.agent.tools.impl;

import com.saran.agent.tools.Tool;
import com.saran.agent.tools.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Deterministic arithmetic so the model never does mental math on prices.
 * Supports add / subtract / multiply / divide on two operands.
 */
@Component
public class CalculatorTool implements Tool {

    @Override
    public String name() {
        return "calculate";
    }

    @Override
    public String description() {
        return "Perform precise arithmetic on two numbers. Use this for any price, "
                + "tax, discount, or quantity calculation instead of computing mentally. "
                + "Supported operations: add, subtract, multiply, divide.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", new String[]{"add", "subtract", "multiply", "divide"}),
                        "a", Map.of("type", "number"),
                        "b", Map.of("type", "number")),
                "required", new String[]{"operation", "a", "b"});
    }

    @Override
    public String execute(Map<String, Object> input) {
        BigDecimal a = toDecimal(input.get("a"), "a");
        BigDecimal b = toDecimal(input.get("b"), "b");
        String op = String.valueOf(input.get("operation"));

        BigDecimal result = switch (op) {
            case "add" -> a.add(b);
            case "subtract" -> a.subtract(b);
            case "multiply" -> a.multiply(b);
            case "divide" -> {
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ToolExecutionException("Division by zero");
                }
                yield a.divide(b, MathContext.DECIMAL64);
            }
            default -> throw new ToolExecutionException("Unknown operation: " + op);
        };
        return result.setScale(Math.min(result.scale(), 6), RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal toDecimal(Object value, String field) {
        if (value == null) {
            throw new ToolExecutionException(field + " is required");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new ToolExecutionException(field + " is not a number: " + value);
        }
    }
}
