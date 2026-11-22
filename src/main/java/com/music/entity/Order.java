package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表
 *
 * 状态枚举：
 *   0 待付款
 *   1 已付款
 *   2 已发货
 *   3 已完成
 *   4 已取消
 *   5 已退款
 */
@Data
@TableName("`order`")   // ⚠️ order 是 MySQL 关键字，必须用反引号包裹
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（唯一） */
    private String orderNo;

    /** 用户 id */
    private Long userId;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 实付金额（totalAmount - discountAmount） */
    private BigDecimal payAmount;

    /** 优惠券抵扣金额（元） */
    private BigDecimal discountAmount;

    /** 收货地址 id（虚拟商品订单可为 null） */
    private Long addressId;

    /** 收件人（快照） */
    private String receiver;

    /** 手机号（快照） */
    private String phone;

    /** 地址详情（快照，省市区 + 详细地址拼接） */
    private String addressDetail;

    /** 状态：0待付款 1已付款 2已发货 3已完成 4已取消 5已退款 */
    private Integer status;

    /** 支付方式：alipay / wechat / mock */
    private String payType;

    /** 支付宝交易号（回调返回，用于对账/退款） */
    private String tradeNo;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime shipTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 快递单号 */
    private String expressNo;

    /** 用户备注 */
    private String remark;
    /** 店铺 id：拆单后一单只属于一个店铺 */
    private Long merchantId;

    /** 支付批次号：同一次结算生成的多个订单共享 */
    private String payNo;
    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer refundStatus;      // 0无 1申请中 2退款中 3成功 4失败 5拒绝
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;


}