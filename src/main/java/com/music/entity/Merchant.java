package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联 user.id（该用户 role 必须 = 1 商家） */
    private Long userId;

    private String shopName;
    private String logo;
    private String description;
    private String contactPhone;
    /** 状态：0禁用 1正常 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
