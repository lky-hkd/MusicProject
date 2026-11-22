package com.music.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Address;
import org.apache.ibatis.annotations.Mapper;
/**
 * 收货地址 Mapper
 *
 * 继承 BaseMapper<Address>，自动拥有基础 CRUD
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
