package com.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.music.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {

    /** 按店名精确查询（店名唯一，开店时查重） */
    @Select("SELECT * FROM merchant WHERE shop_name = #{name} LIMIT 1")
    Merchant getByShopNameExact(@Param("name") String name);

    /** 按店名模糊搜索正常营业的店铺，最多 20 条 */
    @Select("SELECT * FROM merchant WHERE shop_name LIKE CONCAT('%', #{keyword}, '%') " +
            "AND status = 1 ORDER BY id DESC LIMIT 20")
    List<Merchant> searchByShopName(@Param("keyword") String keyword);
}