package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMember> {

    default WorkspaceMember selectByWorkspaceAndUser(Long workspaceId, Long userId) {
        return selectOne(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getUserId, userId));
    }
}
