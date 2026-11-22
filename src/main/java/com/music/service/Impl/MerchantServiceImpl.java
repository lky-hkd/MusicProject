package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.BizException;
import com.music.common.Result;
import com.music.common.Role;
import com.music.dto.MerchantApplyRequest;
import com.music.dto.MerchantShopUpdateRequest;
import com.music.entity.Merchant;
import com.music.entity.User;
import com.music.mapper.MerchantMapper;
import com.music.mapper.UserMapper;
import com.music.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements MerchantService {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private UserMapper userMapper;


    @Override
    public Merchant getByUserId(Long userId) {
        if(userId == null) return null;
        return this.getOne(new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, userId));
    }

    @Override
    public Result<Object> getByMerchantName(String Mname) {
        if(Mname == null) return Result.fail("请输入正确店名");

        Merchant merchant = merchantMapper.getByShopNameExact(Mname.trim());
        return merchant == null ? Result.fail("该店铺不存在") : Result.ok(merchant);
    }


    @Override
    public List<Merchant> searchByShopName(String keyword) {
        if(keyword == null || keyword.trim().isEmpty()) return List.of();

        return merchantMapper.searchByShopName(keyword.trim());
    }

    /**
     * 申请开店：建店铺 + 提升 user.role。
     * 这里只改 DB，Redis 里的 token 由 Controller 刷新。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Merchant applyShop(Long userId, MerchantApplyRequest req) {
        if(userId == null) throw new BizException("请先登录");
        User user = userMapper.selectById(userId);
        if(user == null) throw new BizException("用户不存在");
        // 已开店不允许重复申请
        if(getByUserId(userId)!=null) throw new BizException("该账号已开通店铺，请勿重复申请");
        String shopName = req.getShopName().trim();
        if(merchantMapper.getByShopNameExact(shopName)!=null)
            throw new BizException("店铺名称已被占用，请更换");
        Merchant m = new Merchant();
        m.setUserId(userId);
        m.setShopName(shopName);
        m.setLogo(req.getLogo());
        m.setDescription(req.getDescription());
        m.setContactPhone(req.getContactPhone());
        m.setStatus(1);                       // 默认正常营业
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        this.save(m);

        if(user.getRole()< Role.MERCHANT){
            User upd = new User();
            upd.setId(userId);
            upd.setRole(Role.MERCHANT);
            userMapper.updateById(upd);
        }
        return m;
    }

    @Override
    public Merchant updateShop(Long userId, MerchantShopUpdateRequest req) {
        Merchant m = getByUserId(userId);
        if (m == null) throw new BizException("您还未开通商家店铺");

        if(req.getShopName() != null || !req.getShopName().trim().isEmpty()){
            String newName = req.getShopName().trim();
            //和原来店名不同，要判断是否已存在
            if(!newName.equals(m.getShopName())){
                Merchant exist = merchantMapper.getByShopNameExact(newName);
                //存在
                if(exist != null && !exist.getId().equals(m.getId())){
                    throw new BizException("店铺名称已被占用，请更换");
                };
                //不存在
                m.setShopName(newName);
            };
        };
        if (req.getLogo() != null) m.setLogo(req.getLogo());
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        if (req.getContactPhone() != null) m.setContactPhone(req.getContactPhone());
        m.setUpdateTime(LocalDateTime.now());
        this.updateById(m);
        return m;
    }
}
