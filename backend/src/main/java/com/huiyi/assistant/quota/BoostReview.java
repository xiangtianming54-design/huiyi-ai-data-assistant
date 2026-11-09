package com.huiyi.assistant.quota;

/**
 * 审评人看见的一条待审提额。申请人是用户名，步骤只有审评侧看得到。
 *
 * @param id         申请编号
 * @param applicant  申请人用户名
 * @param reason     由句子和 Token 数生成的理由
 * @param stepName   这一句里 Token 最多的调用步骤
 * @param stepTokens 该步骤的 Token
 * @author huiyi
 */
public record BoostReview(long id, String applicant, String reason, String stepName, int stepTokens) {
}
