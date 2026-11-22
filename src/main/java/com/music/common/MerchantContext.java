package com.music.common;


import com.music.entity.Merchant;
import com.music.service.MerchantService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MerchantContext {

    @Resource
    private MerchantService merchantService;

    public Merchant requireMerchant(Long userId){
        Merchant m = merchantService.getByUserId(userId);
        if(m==null){
            throw new BizException("您还未开通商家店铺");
        }
        if(m.getStatus()==null || m.getStatus() !=1){
            throw new BizException("店铺已被禁用，请联系管理员");
        }
        return m;
    }

    public Long requireMerchantId(Long userId){
        return requireMerchant(userId).getId();
    }

}
