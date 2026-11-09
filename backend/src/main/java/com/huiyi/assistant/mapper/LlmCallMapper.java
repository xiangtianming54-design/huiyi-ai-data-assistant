package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.LlmCall;
import com.huiyi.assistant.quota.RunTokenSum;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
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

    @Select("""
            SELECT COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
                   COALESCE(SUM(completion_tokens), 0) AS completionTokens
            FROM llm_calls
            WHERE run_id = #{runId}
              AND status = 'SUCCESS'
            """)
    @ConstructorArgs({
            @Arg(column = "promptTokens", javaType = int.class),
            @Arg(column = "completionTokens", javaType = int.class)
    })
    RunTokenSum sumByRun(@Param("runId") String runId);
}
