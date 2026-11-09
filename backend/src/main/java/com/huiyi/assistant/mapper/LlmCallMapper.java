package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.LlmCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface LlmCallMapper extends BaseMapper<LlmCall> {

    @Select("""
            SELECT COALESCE(SUM(total_tokens),0)
            FROM llm_calls
            WHERE user_id = #{userId}
              AND status = 'SUCCESS'
              AND created_at >= #{startInclusive}
              AND created_at < #{endExclusive}
            """)
    int sumSuccessTokens(@Param("userId") long userId,
                         @Param("startInclusive") LocalDateTime startInclusive,
                         @Param("endExclusive") LocalDateTime endExclusive);
}
