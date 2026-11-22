package com.music.controller;


import com.music.common.Result;
import com.music.common.UserContext;
import com.music.entity.Address;
import com.music.service.AddressService;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    @Resource
    private AddressService addressService;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

//GET /api/address/list
    @GetMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        List<Address> list = addressService.getAddressByUserId(userId);
        return Result.ok(list);
    }

//    POST /api/address/add
    @PostMapping("/add")
    public Result<?> add(@RequestBody Address address,
                         @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        if(address.getReceiver() == null || address.getReceiver().isEmpty())
            return Result.fail("请填写收件人");
        if(address.getPhone() == null || address.getPhone().isEmpty())
            return Result.fail("请填写手机号");
        if(address.getDetail() == null || address.getDetail().isEmpty())
            return Result.fail("请填写详细地址");
        addressService.addAddress(userId, address);
        return Result.ok("新增成功");
    }

//    POST  /api/address/update
    @PostMapping("/update")
    public Result<?> update(@RequestBody Address address,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        if(address.getId()==null) return Result.fail("地址 id 不能为空");
        addressService.updateAddress(userId, address);
        return Result.ok(null, "更新成功");
    }

//    POST /api/address/delete?addressId=1
    @PostMapping("/delete")
    public Result<?> delete(@RequestParam long addressId,
                            @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        addressService.deleteAddress(userId, addressId);
        return Result.ok(null, "删除成功");
    }

//    POST /api/address/setDefault?addressId=1
    @PostMapping("/setDefault")
    public Result<?> setDefault(@RequestParam long addressId,
                                @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {

        addressService.setDefault(userId, addressId);
        return Result.ok(null, "设置成功");

    }

}
