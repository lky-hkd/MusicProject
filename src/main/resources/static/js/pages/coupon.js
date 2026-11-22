// js/pages/coupon.js
// 我的优惠券

const myCouponState = { status: 0, list: [] };

function formatCouponTime(t) {
    if (!t) return '不限';
    return String(t).replace('T', ' ').slice(0, 16);
}

async function loadMyCoupons() {
    const wrap = document.getElementById('myCouponList');
    if (!wrap) return;
    wrap.innerHTML = '<div class="empty-tip">加载中...</div>';
    try {
        const json = await request('/api/coupon/my');
        myCouponState.list = json.data || [];
        renderMyCoupons();
    } catch (e) {
        console.error('[coupon] 加载我的优惠券失败：', e);
        wrap.innerHTML = '<div class="empty-tip">加载失败，请先登录</div>';
    }
}

function renderMyCoupons() {
    const wrap = document.getElementById('myCouponList');
    if (!wrap) return;

    const list = myCouponState.list.filter(c => c.status === myCouponState.status);
    if (list.length === 0) {
        const emptyText = ['暂无可用优惠券', '暂无已使用优惠券', '暂无已过期优惠券'][myCouponState.status] || '暂无优惠券';
        wrap.innerHTML = `<div class="empty-tip">${emptyText}</div>`;
        return;
    }

    wrap.innerHTML = '';
    list.forEach(c => {
        const card = document.createElement('div');
        card.className = 'my-coupon-card status-' + c.status;
        const statusText = c.status === 0 ? '未使用' : (c.status === 1 ? '已使用' : '已过期');
        const thresholdText = Number(c.threshold) > 0
            ? '满 ¥' + Number(c.threshold).toFixed(2) + ' 可用'
            : '无门槛';

        card.innerHTML = `
          <div class="my-coupon-left">
            <div class="my-coupon-amount"><span>¥</span>${Number(c.discount || 0).toFixed(2)}</div>
            <div class="my-coupon-threshold">${thresholdText}</div>
          </div>
          <div class="my-coupon-right">
            <div class="my-coupon-title">${escapeHtml(c.title || '优惠券')}</div>
            <div class="my-coupon-rules">${escapeHtml(c.rules || '')}</div>
            <div class="my-coupon-time">
              ${c.status === 1
                ? '使用时间：' + formatCouponTime(c.useTime)
                : '有效期至：' + formatCouponTime(c.expireTime)}
            </div>
            <span class="my-coupon-status">${statusText}</span>
          </div>
        `;
        wrap.appendChild(card);
    });
}

let myCouponBound = false;
function bindMyCouponTabs() {
    if (myCouponBound) return;
    myCouponBound = true;
    document.querySelectorAll('#myCouponTabs .my-coupon-tab').forEach(tab => {
        tab.onclick = () => {
            document.querySelectorAll('#myCouponTabs .my-coupon-tab')
                .forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            myCouponState.status = Number(tab.dataset.status);
            renderMyCoupons();
        };
    });
}

registerPageHook('coupon', async () => {
    bindMyCouponTabs();
    await loadMyCoupons();
});
