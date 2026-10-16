package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    default RefreshToken selectActiveByHash(String tokenHash) {
        return selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash)
                .eq(RefreshToken::getStatus, "ACTIVE")
                .gt(RefreshToken::getExpiresAt, LocalDateTime.now()));
    }

    default int revokeByHash(String tokenHash) {
        return update(null, new LambdaUpdateWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash)
                .eq(RefreshToken::getStatus, "ACTIVE")
                .set(RefreshToken::getStatus, "REVOKED")
                .set(RefreshToken::getRevokedAt, LocalDateTime.now()));
    }

    default int revokeAllActive(Long userId) {
        return update(null, new LambdaUpdateWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getStatus, "ACTIVE")
                .set(RefreshToken::getStatus, "REVOKED")
                .set(RefreshToken::getRevokedAt, LocalDateTime.now()));
    }
}
