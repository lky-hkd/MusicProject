package com.music.controller;

import com.music.common.Result;
import com.music.common.Role;
import com.music.common.UserContext;
import com.music.entity.Voucher;
import com.music.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /** 新建优惠券（普通券 / 秒杀券） */
    @PostMapping("seckill")
    public Result<?> addSeckillVoucher(@RequestBody Voucher voucher,
                                       @RequestAttribute(UserContext.ROLE_ATTR) Integer role) {
        if (role == null || role < Role.ADMIN) {
            return Result.fail("无权限操作");
        }
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /** 后台：优惠券列表 */
    @GetMapping("list")
    public Result<?> list(@RequestAttribute(UserContext.ROLE_ATTR) Integer role) {
        if (role == null || role < Role.ADMIN) {
            return Result.fail("无权限操作");
        }
        return Result.ok(voucherService.listAll());
    }

    /** 后台：上架 / 下架 */
    @PostMapping("status")
    public Result<?> status(@RequestParam("id") Long id,
                            @RequestParam("status") Integer status,
                            @RequestAttribute(UserContext.ROLE_ATTR) Integer role) {
        if (role == null || role < Role.ADMIN) {
            return Result.fail("无权限操作");
        }
        voucherService.updateStatus(id, status);
        return Result.ok(null, "操作成功");
    }
}
