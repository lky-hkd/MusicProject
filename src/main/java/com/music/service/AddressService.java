package com.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.music.entity.Address;

import java.util.List;

public interface AddressService extends IService<Address> {
//   查询用户所有地址
    List<Address> getAddressByUserId(Long userId);

//    加,如果 isDefault=1，把其他地址取消默认
    void addAddress(Long userId,Address address);

//    删
    void deleteAddress(Long userId,Long addressId);
//    更新
    void updateAddress(Long userId,Address address);
//    设置默认
    void setDefault(Long userId,Long addressId);
}
