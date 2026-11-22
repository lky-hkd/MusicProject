// js/core/coupon-ui.js
// 优惠券卡片公共渲染逻辑（商城轮播 + 活动中心页共用）
// 依赖：request()、escapeHtml()

const fmtMoney = v => Number(v || 0).toFixed(2);

/**
 * 生成单张优惠券卡片 DOM
 * @param {Object} c 优惠券数据
 * @param {boolean} logged 是否已登录
 * @param {Function} [onChange] 领取/抢购成功后的回调（用于刷新列表）
 */
function buildCouponCard(c, logged, onChange) {
    const isSeckill = c.type === 1;
    const div = document.createElement('div');
    div.className = 'coupon-card ' + (isSeckill ? 'coupon-seckill' : 'coupon-normal');

    const thresholdText = Number(c.threshold) > 0
        ? '满 ¥' + fmtMoney(c.threshold) + ' 可用'
        : '无门槛';

    let meta = `<span class="coupon-meta-item">${thresholdText}</span>`;
    if (isSeckill) {
        meta += `<span class="coupon-meta-item">剩余 ${c.stock == null ? 0 : c.stock} 张</span>`;
        if (c.endTime) {
            meta += `<span class="coupon-meta-item">距结束 <b class="coupon-countdown" data-end="${c.endTime}">--:--:--</b></span>`;
        }
    }
    if (Number(c.validDays) > 0) {
        meta += `<span class="coupon-meta-item">领取后 ${c.validDays} 天有效</span>`;
    }

    const state = couponBtnState(c, logged, isSeckill);
    div.innerHTML = `
        <div class="coupon-card-main">
          <div class="coupon-amount"><span class="coupon-unit">¥</span>${fmtMoney(c.discount)}</div>
          <div class="coupon-info">
            <div class="coupon-title">${escapeHtml(c.title)}
              <span class="coupon-type-tag">${isSeckill ? '秒杀' : '普通'}</span>
            </div>
            <div class="coupon-sub">${escapeHtml(c.subTitle || '')}</div>
            <div class="coupon-meta">${meta}</div>
            <div class="coupon-rules">${escapeHtml(c.rules || '')}</div>
          </div>
        </div>
        <button class="coupon-btn ${isSeckill ? 'seckill' : ''}" ${state.disabled ? 'disabled' : ''}>${state.text}</button>
    `;

    const btn = div.querySelector('.coupon-btn');
    btn.onclick = async () => {
        if (!logged) {
            location.hash = '#/login';
            return;
        }
        btn.disabled = true;
        const origin = btn.innerText;
        btn.innerText = isSeckill ? '抢购中...' : '领取中...';
        try {
            const url = isSeckill
                ? `/api/coupon/seckill/${c.id}`
                : `/api/coupon/receive/${c.id}`;
            await request(url, { method: 'POST' });
            alert(isSeckill ? '抢购成功，可在「我的优惠券」查看' : '领取成功，可在「我的优惠券」查看');
            if (typeof onChange === 'function') onChange();
        } catch (err) {
            alert(err.message || '操作失败');
            btn.disabled = false;
            btn.innerText = origin;
        }
    };
    return div;
}

function couponBtnState(c, logged, isSeckill) {
    if (c.received) return { text: '已领取', disabled: true };
    if (!logged) return { text: isSeckill ? '登录抢购' : '登录领取', disabled: false };
    if (isSeckill) {
        const now = Date.now();
        if (c.beginTime && now < new Date(c.beginTime).getTime()) {
            return { text: '未开始', disabled: true };
        }
        if (c.endTime && now > new Date(c.endTime).getTime()) {
            return { text: '已结束', disabled: true };
        }
        if (c.soldOut) return { text: '已抢光', disabled: true };
        return { text: '立即抢购', disabled: false };
    }
    return { text: '立即领取', disabled: false };
}

/** 启动秒杀倒计时（作用于指定容器内所有 .coupon-countdown） */
function startCouponCountdown(containerSelector) {
    const root = containerSelector ? document.querySelector(containerSelector) : document;
    if (!root) return;
    if (root._couponCountdownTimer) clearInterval(root._couponCountdownTimer);
    const tick = () => {
        root.querySelectorAll('.coupon-countdown').forEach(el => {
            const end = new Date(el.dataset.end).getTime();
            const diff = end - Date.now();
            if (diff <= 0) {
                el.innerText = '00:00:00';
                return;
            }
            const h = String(Math.floor(diff / 3600000)).padStart(2, '0');
            const m = String(Math.floor((diff % 3600000) / 60000)).padStart(2, '0');
            const s = String(Math.floor((diff % 60000) / 1000)).padStart(2, '0');
            el.innerText = `${h}:${m}:${s}`;
        });
    };
    tick();
    root._couponCountdownTimer = setInterval(tick, 1000);
}
