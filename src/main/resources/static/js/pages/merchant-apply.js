// js/pages/merchant-apply.js
// 开店申请：普通用户 -> 商家
// 依赖：request(url, options)、registerPageHook()

registerPageHook('merchant-apply', () => {
    const btn = document.getElementById('applySubmitBtn');
    if (!btn || btn.dataset.bound) return;
    btn.dataset.bound = '1';

    const tip = () => document.getElementById('applyTip');
    const showTip = (msg, ok) => {
        const t = tip();
        if (!t) return;
        t.style.color = ok ? '#4ade80' : '#f87171';
        t.innerText = msg;
    };
    const val = (id) => (document.getElementById(id)?.value || '').trim();

    btn.addEventListener('click', async () => {
        const shopName = val('applyShopName');
        if (!shopName) { showTip('店铺名称不能为空', false); return; }

        const body = {
            shopName,
            logo: val('applyLogo') || null,
            description: val('applyDesc') || null,
            contactPhone: val('applyPhone') || null
        };

        btn.disabled = true;
        try {
            const res = await request('/api/user/applyMerchant', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            if (res.success) {
                showTip('开店成功，正在进入商家中心...', true);
                setTimeout(() => { location.hash = '#/merchant-center'; }, 800);
            } else {
                showTip(res.msg || '申请失败', false);
            }
        } catch (e) {
            showTip('网络错误，请重试', false);
        } finally {
            btn.disabled = false;
        }
    });
});