package com.music.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String nickname;
    private String avatar;
    /** 0 保密 / 1 男 / 2 女 */
    private Integer gender;
    /** yyyy-MM-dd，空字符串表示清空 */
    private String birthday;
    private String signature;
}
