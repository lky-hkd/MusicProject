package com.music.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.music.entity.ModerationAudit;
import com.music.mapper.ModerationAuditMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计表保留期清理：只留最近 N 天，避免用户内容相关的记录无限增长。
 * 保留天数由 app.moderation.audit-retention-days 控制，<=0 关闭。
 */
@Slf4j
@Component
public class ModerationAuditCleanTask {

    @Resource
    private ModerationAuditMapper auditMapper;

    @Value("${app.moderation.audit-retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * ?")
    public void clean() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        try {
            int deleted = auditMapper.delete(new QueryWrapper<ModerationAudit>()
                    .lt("create_time", before));
            if (deleted > 0) {
                log.info("[moderation] 清理过期审计 {} 条（早于 {}）", deleted, before);
            }
        } catch (Exception e) {
            log.warn("[moderation] 清理过期审计失败：{}", e.getMessage());
        }
    }
}
