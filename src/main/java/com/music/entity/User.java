package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private int role;
    private String phone;
    private LocalDateTime createTime;
    private Integer followingCount; // 关注数
    private Integer followerCount;  // 粉丝数

    /** 0 保密 / 1 男 / 2 女 */
    private Integer gender;
    private LocalDate birthday;
    private String signature;
    /** 是否真正设置过密码：0 短信登录自动建号，1 已设置 */
    private Integer passwordSet;

    // ---- VIP 状态(瞬态,不落库,仅 getUserInfo 返回用) ----
    @TableField(exist = false)
    private Boolean vip;
    @TableField(exist = false)
    private LocalDateTime vipExpireTime;
    @TableField(exist = false)
    private Boolean isVipForever;
}
