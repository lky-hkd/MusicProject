package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.common.Result;
import com.music.dto.MerchantApplyRequest;
import com.music.dto.MerchantShopUpdateRequest;
import com.music.entity.Merchant;

import java.util.List;

public interface MerchantService extends IService<Merchant> {

    /** 按登录用户 id 查店铺（找不到返回 null） */
    Merchant getByUserId(Long userId);
    /**精准查询*/
    Result<Object> getByMerchantName(String Mname);
    /** 按店名模糊搜索正常营业的店铺 */
    List<Merchant> searchByShopName(String keyword);

    /** 申请开店：建店铺 + 提升用户角色为商家 */
    Merchant applyShop(Long userId, MerchantApplyRequest req);

    /** 修改店铺资料（店名校验唯一） */
    Merchant updateShop(Long userId, MerchantShopUpdateRequest req);
}
