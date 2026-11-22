// js/pages/merchant-shop.js
// 商家店铺页：店铺信息 + 在售商品

(function () {
    let merchantId = null;
    let page = 1;
    let pages = 1;
    let bound = false;

    function renderHeader(m) {
        const el = document.getElementById('merchantShopHeader');
        if (!el) return;
        el.innerHTML = `
            <div class="ms-card">
                ${m.logo
                    ? `<img class="ms-logo" src="${m.logo}" alt="">`
                    : `<div class="ms-logo pd-avatar-text">${escapeHtml((m.shopName || '店').charAt(0))}</div>`}
                <div class="ms-info">
                    <div class="ms-name">${escapeHtml(m.shopName || '')}</div>
                    <div class="ms-desc">${escapeHtml(m.description || '')}</div>
                    <div class="ms-count">在售商品 ${m.productCount || 0} 件</div>
                </div>
                <button class="ms-contact" type="button" id="msContactBtn">联系商家</button>
            </div>
        `;
        const btn = document.getElementById('msContactBtn');
        if (btn) {
            btn.onclick = async () => {
                try {
                    const r = await request(`/api/chat/open-merchant?merchantId=${m.id}`, { method: 'POST' });
                    location.hash = '#/chat?id=' + r.data.id;
                } catch (e) {
                    alert(e.message || '打开会话失败');
                }
            };
        }
    }

    function renderProduct(p) {
        const isVirtual = p.productType === 'virtual';
        const tagClass = isVirtual ? 'virtual' : 'physical';
        const tagText = isVirtual ? '虚拟' : '实体';
        return `
            <div class="shop-card reveal" onclick="location.hash='#/product?id=${p.id}'">
                <span class="tag ${tagClass}">${tagText}</span>
                <img class="shop-card-image" src="${p.coverUrl || ''}" alt="${escapeHtml(p.name)}" loading="lazy">
                <div class="shop-card-title">${escapeHtml(p.name)}</div>
                <div class="shop-card-subtitle">${escapeHtml(p.subtitle || '')}</div>
                <div class="shop-card-price">
                    ${p.price}
                    ${p.originalPrice ? `<span class="shop-card-original">¥${p.originalPrice}</span>` : ''}
                </div>
            </div>
        `;
    }

    async function loadProducts(reset) {
        const grid = document.getElementById('merchantShopGrid');
        const moreBtn = document.getElementById('merchantShopMore');
        if (!grid || !merchantId) return;

        if (reset) {
            page = 1;
            pages = 1;
            grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;">加载中...</div>';
        }

        try {
            const json = await request(`/api/product/list?merchantId=${merchantId}&page=${page}&size=12`);
            const data = json.data || {};
            const list = data.records || [];
            pages = data.pages || 1;

            if (reset) grid.innerHTML = '';
            if (!list.length) {
                if (reset) {
                    grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;">该店铺还没有上架商品</div>';
                }
                if (moreBtn) moreBtn.style.display = 'none';
                return;
            }
            grid.insertAdjacentHTML('beforeend', list.map(renderProduct).join(''));
            if (moreBtn) {
                moreBtn.style.display = page < pages ? '' : 'none';
            }
        } catch (e) {
            console.error('[merchant-shop] 商品加载失败：', e);
            if (reset) {
                grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;">商品加载失败</div>';
            }
        }
    }

    function bindOnce() {
        if (bound) return;
        bound = true;
        const moreBtn = document.getElementById('merchantShopMore');
        if (moreBtn) {
            moreBtn.onclick = () => {
                if (page >= pages) return;
                page++;
                loadProducts(false);
            };
        }
    }

    registerPageHook('merchant-shop', async () => {
        bindOnce();
        merchantId = Number(getUrlParam('id'));
        const header = document.getElementById('merchantShopHeader');
        const grid = document.getElementById('merchantShopGrid');

        if (!merchantId) {
            if (header) header.innerHTML = '';
            if (grid) grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;">店铺不存在</div>';
            return;
        }

        if (header) header.innerHTML = '<div class="ms-card"><div class="ms-info">店铺加载中...</div></div>';
        try {
            const json = await request(`/api/product/merchant/${merchantId}`);
            renderHeader(json.data || {});
        } catch (e) {
            if (header) header.innerHTML = '<div class="ms-card"><div class="ms-info">店铺不存在或已关闭</div></div>';
        }
        await loadProducts(true);
    });
})();
