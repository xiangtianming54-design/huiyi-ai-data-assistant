package com.huiyi.assistant.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.KnowledgeSpaceMapper;
import com.huiyi.assistant.model.entity.KnowledgeSpace;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeSpaceService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String DEFAULT_NAME = "默认";
    public static final String ERR_MISSING = "知识空间不存在或不属于当前工作区";
    public static final String ERR_DEFAULT_ARCHIVE = "默认知识空间不可归档";
    public static final String ERR_ARCHIVED_UPLOAD = "不能向已归档空间上传";
    public static final String ERR_DUPLICATE_NAME = "知识空间名称已存在";

    private final KnowledgeSpaceMapper knowledgeSpaceMapper;
    private final AuditService auditService;

    public KnowledgeSpaceService(KnowledgeSpaceMapper knowledgeSpaceMapper, AuditService auditService) {
        this.knowledgeSpaceMapper = knowledgeSpaceMapper;
        this.auditService = auditService;
    }

    public KnowledgeSpace ensureDefault(long workspaceId, long createdBy) {
        KnowledgeSpace existing = findDefault(workspaceId);
        if (existing != null) {
            return existing;
        }
        KnowledgeSpace space = new KnowledgeSpace();
        space.setWorkspaceId(workspaceId);
        space.setName(DEFAULT_NAME);
        space.setStatus(STATUS_ACTIVE);
        space.setIsDefault(true);
        space.setCreatedBy(createdBy);
        knowledgeSpaceMapper.insert(space);
        auditService.record(AuditAction.KNOWLEDGE_SPACE_CREATED, "SUCCESS", createdBy, null, workspaceId,
                "knowledge_space", String.valueOf(space.getId()), Map.of("default", true));
        return space;
    }

    public KnowledgeSpace requireActiveSpace(long workspaceId, Long spaceId) {
        if (spaceId == null) {
            return ensureDefault(workspaceId, 0L);
        }
        KnowledgeSpace space = loadInWorkspace(workspaceId, spaceId);
        if (!STATUS_ACTIVE.equals(space.getStatus())) {
            throw new BizException(400, ERR_ARCHIVED_UPLOAD);
        }
        return space;
    }

    public List<Long> listActiveIds(long workspaceId) {
        return listActive(workspaceId).stream().map(KnowledgeSpace::getId).toList();
    }

    public List<KnowledgeSpace> listActive(long workspaceId) {
        return knowledgeSpaceMapper.selectList(new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getWorkspaceId, workspaceId)
                .eq(KnowledgeSpace::getStatus, STATUS_ACTIVE)
                .orderByDesc(KnowledgeSpace::getIsDefault)
                .orderByAsc(KnowledgeSpace::getId));
    }

    public KnowledgeSpace create(long workspaceId, long userId, String name, String description) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(400, "知识空间名称不能为空");
        }
        KnowledgeSpace space = new KnowledgeSpace();
        space.setWorkspaceId(workspaceId);
        space.setName(name.trim());
        space.setDescription(description);
        space.setStatus(STATUS_ACTIVE);
        space.setIsDefault(false);
        space.setCreatedBy(userId);
        persistInsert(space);
        auditService.record(AuditAction.KNOWLEDGE_SPACE_CREATED, "SUCCESS", userId, null, workspaceId,
                "knowledge_space", String.valueOf(space.getId()), Map.of("name", space.getName()));
        return space;
    }

    public KnowledgeSpace update(long workspaceId, long spaceId, String name, String description, long actorUserId) {
        KnowledgeSpace space = loadInWorkspace(workspaceId, spaceId);
        if (StringUtils.hasText(name)) {
            space.setName(name.trim());
        }
        if (description != null) {
            space.setDescription(description);
        }
        persistUpdate(space);
        auditService.record(AuditAction.KNOWLEDGE_SPACE_UPDATED, "SUCCESS", actorUserId, null, workspaceId,
                "knowledge_space", String.valueOf(spaceId), Map.of());
        return space;
    }

    public void archive(long workspaceId, long spaceId, long actorUserId) {
        KnowledgeSpace space = loadInWorkspace(workspaceId, spaceId);
        if (Boolean.TRUE.equals(space.getIsDefault())) {
            throw new BizException(400, ERR_DEFAULT_ARCHIVE);
        }
        space.setStatus(STATUS_ARCHIVED);
        persistUpdate(space);
        auditService.record(AuditAction.KNOWLEDGE_SPACE_ARCHIVED, "SUCCESS", actorUserId, null, workspaceId,
                "knowledge_space", String.valueOf(spaceId), Map.of());
    }

    private void persistInsert(KnowledgeSpace space) {
        try {
            knowledgeSpaceMapper.insert(space);
        } catch (DuplicateKeyException e) {
            throw new BizException(400, ERR_DUPLICATE_NAME);
        }
    }

    private void persistUpdate(KnowledgeSpace space) {
        try {
            knowledgeSpaceMapper.updateById(space);
        } catch (DuplicateKeyException e) {
            throw new BizException(400, ERR_DUPLICATE_NAME);
        }
    }

    private KnowledgeSpace findDefault(long workspaceId) {
        return knowledgeSpaceMapper.selectOne(new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getWorkspaceId, workspaceId)
                .eq(KnowledgeSpace::getIsDefault, true)
                .last("LIMIT 1"));
    }

    private KnowledgeSpace loadInWorkspace(long workspaceId, long spaceId) {
        KnowledgeSpace space = knowledgeSpaceMapper.selectById(spaceId);
        if (space == null || space.getWorkspaceId() == null || space.getWorkspaceId() != workspaceId) {
            throw new BizException(400, ERR_MISSING);
        }
        return space;
    }
}
