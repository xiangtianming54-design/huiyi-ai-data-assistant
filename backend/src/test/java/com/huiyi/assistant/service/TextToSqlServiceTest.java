package com.huiyi.assistant.service;

import com.huiyi.assistant.common.util.PromptTemplateLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void generateSqlRendersPromptAndExtracts() {
        // 打桩：SchemaService 返回表结构，LlmService 返回 JSON
        SchemaService schemaService = mock(SchemaService.class);
        when(schemaService.getSchemaDescription()).thenReturn("表 sales_orders（销售订单表）:\n  - id: BIGINT");
        LlmService llmService = mock(LlmService.class);
        when(llmService.chat(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            assertTrue(prompt.contains("表 sales_orders"), "Prompt 应包含 Schema");
            assertTrue(prompt.contains("销售额"), "Prompt 应包含业务口径");
            assertTrue(prompt.contains("上个月销售额"), "Prompt 应包含用户问题");
            return "{\"sql\": \"SELECT * FROM sales_orders LIMIT 200\"}";
        });

        TextToSqlService textToSqlService = new TextToSqlService(llmService, schemaService, new PromptTemplateLoader());
        String sql = textToSqlService.generateSql("上个月销售额最高的 10 个商品是什么？");

        assertEquals("SELECT * FROM sales_orders LIMIT 200", sql);
    }
}
