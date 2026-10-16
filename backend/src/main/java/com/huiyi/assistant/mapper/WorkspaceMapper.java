package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.Workspace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkspaceMapper extends BaseMapper<Workspace> {

    default Workspace selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<Workspace>().eq(Workspace::getCode, code));
    }
}
