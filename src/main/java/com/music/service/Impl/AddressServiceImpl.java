package com.music.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.music.common.BizException;
import com.music.entity.Address;
import com.music.mapper.AddressMapper;
import com.music.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper,Address> implements AddressService {
    @Override
    public List<Address> getAddressByUserId(Long userId) {
        LambdaQueryWrapper<Address> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Address::getUserId, userId);
        queryWrapper.orderByDesc(Address::getIsDefault);
        queryWrapper.orderByDesc(Address::getCreateTime);
        return this.list(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAddress(Long userId, Address address) {
        address.setUserId(userId);
        address.setCreateTime(LocalDateTime.now());
// 如果是默认地址，先把其他地址取消默认
        if(address.getIsDefault()!=null && address.getIsDefault()==1){
            clearDefault(userId);
        }else{
            long count = this.count(new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId));
            if(count==0){
                address.setIsDefault(1);
            }else{
                address.setIsDefault(0);
            }
        }
        this.save(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long addressId) {
        Address address = this.getById(addressId);
        if(address==null){
            return;
        }
        if(!address.getUserId().equals(userId)){
            throw new BizException("无权操作此地址");
        }
        this.removeById(addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, Address address) {
        Address oldAddress = this.getById(address.getId());
        if(oldAddress==null){
            throw new BizException("地址不存在");
        }
        if(!oldAddress.getUserId().equals(userId)){
            throw new BizException("无权操作此地址");
        }
        if(address.getIsDefault()!=null && address.getIsDefault()==1){
            clearDefault(userId);
        }
        address.setUserId(userId);
        this.updateById(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        Address address = this.getById(addressId);
        if (address == null) {
            throw new BizException("地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BizException("无权操作此地址");
        }
        clearDefault(userId);

        LambdaUpdateWrapper<Address> queryWrapper = new LambdaUpdateWrapper<>();
        queryWrapper.eq(Address::getId, addressId);
        queryWrapper.set(Address::getIsDefault, 1);
        this.update(queryWrapper);
    }

    private void clearDefault(Long userId) {
        LambdaUpdateWrapper<Address> uw = new LambdaUpdateWrapper<>();   // ✅
        uw.set(Address::getIsDefault, 0);
        uw.eq(Address::getUserId, userId);
        this.update(uw);
    }
}
