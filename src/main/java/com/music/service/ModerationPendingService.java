package com.music.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.music.common.ModerationContentType;
import com.music.entity.ModerationPending;
import com.music.entity.User;
import com.music.mapper.ModerationPendingMapper;
import com.music.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待审内容（昵称、个性签名）：
 * 这类内容没有独立业务表，命中可疑后不能直接生效，
 * 先落 moderation_pending，等管理员通过后再写回用户表。
 */
@Slf4j
@Service
public class ModerationPendingService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @Resource
    private ModerationPendingMapper pendingMapper;

    @Resource
    private UserMapper userMapper;

    /** 提交待审：同一用户同一字段只保留最新一条待审记录 */
    @Transactional(rollbackFor = Exception.class)
    public void submit(ModerationContentType type, Long userId, String content) {
        pendingMapper.delete(new QueryWrapper<ModerationPending>()
                .eq("user_id", userId)
                .eq("content_type", type.name())
                .eq("status", STATUS_PENDING));
        ModerationPending pending = new ModerationPending();
        pending.setContentType(type.name());
        pending.setUserId(userId);
        pending.setContent(content);
        pending.setStatus(STATUS_PENDING);
        pending.setCreateTime(LocalDateTime.now());
        pendingMapper.insert(pending);
    }

    public List<ModerationPending> list(String type, int limit) {
        return pendingMapper.selectList(new QueryWrapper<ModerationPending>()
                .eq("content_type", type)
                .eq("status", STATUS_PENDING)
                .orderByAsc("id")
                .last("limit " + limit));
    }

    /** 通过：写回用户表 */
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long id) {
        ModerationPending pending = pendingMapper.selectById(id);
        if (pending == null || pending.getStatus() != STATUS_PENDING) {
            return false;
        }
        User update = new User();
        update.setId(pending.getUserId());
        if (ModerationContentType.NICKNAME.name().equals(pending.getContentType())) {
            update.setNickname(pending.getContent());
        } else if (ModerationContentType.SIGNATURE.name().equals(pending.getContentType())) {
            update.setSignature(pending.getContent());
        } else {
            return false;
        }
        userMapper.updateById(update);
        changeStatus(id, STATUS_APPROVED);
        log.info("[moderation] 待审内容通过 type={} userId={}", pending.getContentType(), pending.getUserId());
        return true;
    }

    /** 驳回：丢弃，用户原昵称/签名保持不变 */
    public boolean reject(Long id) {
        ModerationPending pending = pendingMapper.selectById(id);
        if (pending == null || pending.getStatus() != STATUS_PENDING) {
            return false;
        }
        changeStatus(id, STATUS_REJECTED);
        log.info("[moderation] 待审内容驳回 type={} userId={}", pending.getContentType(), pending.getUserId());
        return true;
    }

    private void changeStatus(Long id, int status) {
        ModerationPending update = new ModerationPending();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        pendingMapper.updateById(update);
    }
}
