package com.saran.agent.tools.impl;

import com.saran.agent.tools.Tool;
import com.saran.agent.tools.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Looks up an order in a (mock) order database.
 * In a real system this would call an order service or repository.
 */
@Component
public class OrderStatusTool implements Tool {

    private static final Map<String, String> ORDERS = Map.of(
            "A-1001", "Shipped via FedEx, expected delivery 2026-06-13, destination Austin TX",
            "A-1002", "Processing in warehouse, expected to ship within 24 hours",
            "A-1003", "Delivered 2026-06-08, signed by recipient",
            "A-1004", "On hold: payment verification required"
    );

    @Override
    public String name() {
        return "get_order_status";
    }

    @Override
    public String description() {
        return "Look up the current status of a customer order by its order ID "
                + "(format: letter dash digits, e.g. A-1001). Returns shipping status "
                + "and expected delivery information.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "order_id", Map.of(
                                "type", "string",
                                "description", "The order ID, e.g. A-1001")),
                "required", new String[]{"order_id"});
    }

    @Override
    public String execute(Map<String, Object> input) {
        Object raw = input.get("order_id");
        if (raw == null) {
            throw new ToolExecutionException("order_id is required");
        }
        String orderId = raw.toString().trim().toUpperCase();
        String status = ORDERS.get(orderId);
        if (status == null) {
            throw new ToolExecutionException("No order found with ID " + orderId);
        }
        return "Order " + orderId + ": " + status;
    }
}
