package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_vip")
public class UserVip {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会员等级(预留) */
    private Integer vipLevel;

    /** 到期时间,NULL=永久 */
    private LocalDateTime expireTime;

    /** 1永久 0有期限 */
    private Integer isForever;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前是否有效:永久 或 到期时间在未来 */
    public boolean isValid() {
        if (isForever != null && isForever == 1) return true;
        return expireTime != null && expireTime.isAfter(LocalDateTime.now());
    }
}
