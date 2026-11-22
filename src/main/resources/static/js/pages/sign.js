// js/pages/sign.js
// 签到中心：签到、累计进度、满 30 天兑换 VIP

(function () {
    let bound = false;
    let busy = false;

    function $(id) {
        return document.getElementById(id);
    }

    function setText(id, value) {
        const el = $(id);
        if (el) el.textContent = value;
    }

    function renderStatus(d) {
        setText('signContinuous', d.continuousDays || 0);
        setText('signMonth', d.monthDays || 0);
        setText('signTotal', d.totalDays || 0);

        const milestone = d.milestoneDays || 30;
        const reward = d.rewardVipDays || 7;

        const btn = $('signBtn');
        if (btn) {
            btn.disabled = !!d.signedToday;
            btn.classList.toggle('signed', !!d.signedToday);
        }

        const tip = $('signTip');
        if (tip) {
            tip.textContent = d.signedToday ? '今日已签到，明天再来' : '今日还未签到';
        }

        const progress = Math.min(100, Math.round(((d.progressDays || 0) / milestone) * 100));
        const fill = $('signProgressFill');
        if (fill) fill.style.width = progress + '%';
        setText('signProgressText',
            `累计 ${d.totalDays || 0} / ${d.nextMilestone || milestone} 天，每满 ${milestone} 天可兑换 ${reward} 天 VIP`);

        const redeem = $('signRedeemBtn');
        if (redeem) redeem.style.display = d.redeemable ? '' : 'none';
    }

    async function loadStatus() {
        try {
            const json = await request('/api/user/sign/status');
            renderStatus(json.data || {});
        } catch (e) {
            setText('signContinuous', 0);
            setText('signMonth', 0);
            setText('signTotal', 0);
            const tip = $('signTip');
            if (tip) tip.textContent = '请先登录后再签到';
            const btn = $('signBtn');
            if (btn) btn.disabled = true;
            const redeem = $('signRedeemBtn');
            if (redeem) redeem.style.display = 'none';
        }
    }

    async function doSign() {
        if (busy) return;
        busy = true;
        try {
            const json = await request('/api/user/sign', { method: 'POST' });
            await loadStatus();
            const tip = $('signTip');
            if (tip) tip.textContent = (json && json.msg) || '签到成功';
        } catch (e) {
            const tip = $('signTip');
            if (tip) tip.textContent = e.message || '签到失败，请稍后重试';
        } finally {
            busy = false;
        }
    }

    async function doRedeem() {
        if (busy) return;
        busy = true;
        try {
            const json = await request('/api/user/sign/vip', { method: 'POST' });
            const d = json.data || {};
            const expire = d.vipExpireTime
                ? '，到期时间：' + String(d.vipExpireTime).replace('T', ' ')
                : '';
            alert('兑换成功，VIP 已到账' + expire);
            await loadStatus();
        } catch (e) {
            alert(e.message || '兑换失败，请稍后重试');
        } finally {
            busy = false;
        }
    }

    function bind() {
        if (bound) return;
        bound = true;
        const signBtn = $('signBtn');
        if (signBtn) signBtn.addEventListener('click', doSign);
        const redeemBtn = $('signRedeemBtn');
        if (redeemBtn) redeemBtn.addEventListener('click', doRedeem);
    }

    registerPageHook('sign', function () {
        bind();
        loadStatus();
    });
})();
