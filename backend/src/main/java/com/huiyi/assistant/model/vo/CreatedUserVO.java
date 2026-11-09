package com.huiyi.assistant.model.vo;

/**
 * 新建账号的结果。没有登录令牌。
 *
 * @param id       新用户编号
 * @param username 用户名
 * @param tier     等级，2 或 3
 * @author huiyi
 */
public record CreatedUserVO(Long id, String username, int tier) {
}
