// js/pages/activity.js
// 活动中心页（展示所有可领取优惠券）
// 依赖：request()、buildCouponCard()、startCouponCountdown()

async function loadActivityList() {
    const wrap = document.getElementById('activityList');
    if (!wrap) return;
    try {
        const json = await request('/api/coupon/activity');
        const data = json.data || {};
        const list = data.list || [];

        const tip = document.getElementById('activityTip');
        if (tip) tip.innerText = data.logged
            ? '每券限领一次，领完即止'
            : '登录后即可领取优惠券';

        if (list.length === 0) {
            wrap.innerHTML = '<div class="coupon-empty">暂无进行中的优惠券活动</div>';
            return;
        }
        wrap.innerHTML = '';
        list.forEach(c => wrap.appendChild(buildCouponCard(c, !!data.logged, loadActivityList)));
        startCouponCountdown('#view-activity');
    } catch (e) {
        console.error('[activity] 加载活动列表失败：', e);
        wrap.innerHTML = '<div class="coupon-empty">活动加载失败，请稍后重试</div>';
    }
}

registerPageHook('activity', () => {
    loadActivityList();
});
