package com.huiyi.assistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TextToSqlService SQL 提取（三级兜底）单元测试。
 *
 * @author huiyi
 */
class TextToSqlServiceTest {

    private final TextToSqlService service = new TextToSqlService(null, null, null);

    @Test
    void extractsFromPlainJson() {
        assertEquals("SELECT * FROM sales_orders LIMIT 200",
                service.extractSql("{\"sql\": \"SELECT * FROM sales_orders LIMIT 200\"}"));
    }

    @Test
    void extractsFromJsonCodeBlock() {
        String output = "```json\n{\"sql\": \"SELECT 1\"}\n```";
        assertEquals("SELECT 1", service.extractSql(output));
    }

    @Test
    void extractsFromSqlCodeBlock() {
        String output = "为您生成：\n```sql\nSELECT * FROM products LIMIT 10\n```";
        assertEquals("SELECT * FROM products LIMIT 10", service.extractSql(output));
    }

    @Test
    void extractsBareSelect() {
        String output = "SELECT product_name FROM products LIMIT 5;";
        assertEquals("SELECT product_name FROM products LIMIT 5", service.extractSql(output));
    }

    @Test
    void returnsNullForNonsense() {
        assertNull(service.extractSql("抱歉，我无法理解您的问题。"));
        assertNull(service.extractSql(""));
        assertNull(service.extractSql(null));
    }
}
