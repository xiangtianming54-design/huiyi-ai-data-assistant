package com.huiyi.assistant.common.util;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板加载工具：读取 classpath 模板并替换 {{var}} 占位符，模板带缓存。
 *
 * @author huiyi
 */
@Slf4j
@Component
public class PromptTemplateLoader {

    /** 占位符格式：{{var}} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 渲染模板。
     *
     * @param location classpath 路径，如 prompts/rag-system.md
     * @param variables 占位符变量
     * @return 渲染后的文本
     */
    public String render(String location, Map<String, String> variables) {
        String template = templateCache.computeIfAbsent(location, this::readResource);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                log.warn("Prompt 占位符缺少变量: location={}, key={}", location, key);
                value = "";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 读取 classpath 模板文件。
     */
    private String readResource(String location) {
        try {
            return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Prompt 模板读取失败: {}", location, e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "Prompt 模板读取失败: " + location);
        }
    }
}
