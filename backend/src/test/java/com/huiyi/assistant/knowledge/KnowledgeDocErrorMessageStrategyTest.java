package com.huiyi.assistant.knowledge;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeDocErrorMessageStrategyTest {

    @Test
    void errorMessageUpdateIncludesNull() throws Exception {
        TableField field = KnowledgeDoc.class.getDeclaredField("errorMessage").getAnnotation(TableField.class);
        assertNotNull(field);
        assertEquals(FieldStrategy.ALWAYS, field.updateStrategy());
    }
}
