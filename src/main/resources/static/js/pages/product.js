// js/pages/product.js
// 商品详情页

async function loadProductDetail() {
    const wrap = document.getElementById('productDetail');
    if (!wrap) return;
    wrap.innerHTML = '<div class="empty-tip">加载中...</div>';

    // 从 URL 拿 id
    const id = getUrlParam('id');
    if (!id) {
        wrap.innerHTML = '<div class="empty-tip">商品不存在</div>';
        return;
    }

    try {
        const json = await request(`/api/product/detail?id=${id}`);
        if (!json.success || !json.data) {
            wrap.innerHTML = '<div class="empty-tip">商品不存在</div>';
            return;
        }

        const p = json.data;
        const typeLabel = p.productType === 'virtual' ? '虚拟商品' : '实体商品';

        wrap.innerHTML = `
            <div class="product-detail-wrap">
                <img class="product-detail-cover" src="${p.coverUrl || ''}" alt="${escapeHtml(p.name)}">
                <div class="product-detail-info">
                    <div class="product-detail-name">${escapeHtml(p.name)}</div>
                    <div class="product-detail-subtitle">${escapeHtml(p.subtitle || '')}</div>

                    <div class="product-detail-price-box">
                        <span class="product-detail-price">${p.price}</span>
                        ${p.originalPrice ? `<span class="product-detail-original">¥${p.originalPrice}</span>` : ''}
                    </div>

                    <div class="product-detail-row">
                        <span class="label">类型：</span>
                        <span>${typeLabel}</span>
                    </div>
                    ${p.relatedSinger ? `
                    <div class="product-detail-row">
                        <span class="label">关联歌手：</span>
                        <span>${escapeHtml(p.relatedSinger)}</span>
                    </div>` : ''}
                    <div class="product-detail-row">
                        <span class="label">库存：</span>
                        <span>${p.stock}</span>
                    </div>
                    <div class="product-detail-row">
                        <span class="label">销量：</span>
                        <span>${p.sales || 0}</span>
                    </div>

                    <div class="product-detail-actions">
                        <button class="btn-primary" id="addCartBtn">加入购物车</button>
                        <button class="btn-secondary" id="buyNowBtn">立即购买</button>
                        ${p.merchantId ? '<button class="btn-secondary" id="contactSellerBtn">联系卖家</button>' : ''}
                    </div>
                </div>
            </div>

            <div class="pd-sections">
                <section class="pd-section">
                    <h3 class="pd-section-title">商品详情</h3>
                    <div class="pd-desc">${escapeHtml(p.description || '暂无商品描述')}</div>
                </section>

                <section class="pd-section">
                    <h3 class="pd-section-title">商品评价 <span class="pd-avg" id="pdReviewAvg"></span></h3>
                    <div class="pd-review-list" id="pdReviewList">
                        <div class="empty-tip">加载中...</div>
                    </div>
                    <div class="pd-review-more">
                        <button type="button" id="pdReviewMore" style="display:none;">加载更多评价</button>
                    </div>
                </section>

                <section class="pd-section" id="pdMerchantSection" style="display:none;"></section>
            </div>
        `;

        document.getElementById('addCartBtn').onclick = async () => {
            try {
                const json = await request(`/api/cart/add?productId=${p.id}&quantity=1`, {
                    method: 'POST'
                });
                if (json.success) {
                    alert('已加入购物车 ✅');
                    refreshCartBadge();   // 刷新顶部角标
                } else {
                    if (json.msg && json.msg.includes('登录')) {
                        if (confirm('请先登录，是否前往登录页？')) {
                            location.hash = '#/login';
                        }
                    } else {
                        alert(json.msg || '加入失败');
                    }
                }
            } catch (err) {
                console.error('[product] 加入购物车失败：', err);
                alert('网络异常');
            }
        };
        document.getElementById('buyNowBtn').onclick = () => {
            localStorage.setItem('checkoutData',JSON.stringify({
                source: 'direct',
                items: [{productId: p.id,quantity: 1}]
            }));
            location.hash= '#/order-confirm';
        };

        const sellerBtn = document.getElementById('contactSellerBtn');
        if (sellerBtn) {
            sellerBtn.onclick = async () => {
                try {
                    const r = await request(`/api/chat/open-merchant?merchantId=${p.merchantId}`, { method: 'POST' });
                    location.hash = '#/chat?id=' + r.data.id;
                } catch (e) {
                    alert(e.message || '打开会话失败');
                }
            };
        }

        // 评价 + 商家信息
        initProductReviews(p.id);
        initProductMerchant(p.merchantId);
    } catch (err) {
        console.error('[product] 加载失败：', err);
        wrap.innerHTML = '<div class="empty-tip">网络异常</div>';
    }
}

// ========== 商品评价 ==========
let pdReviewPage = 1;
let pdReviewPages = 1;
let pdReviewProductId = null;

function pdStars(n) {
    n = Number(n) || 5;
    return '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n));
}

function renderPdReview(r) {
    const avatar = r.avatar
        ? `<img class="pd-review-avatar" src="${r.avatar}" alt="">`
        : `<div class="pd-review-avatar pd-avatar-text">${escapeHtml((r.userName || '匿').charAt(0))}</div>`;
    const images = (r.images || []).length
        ? `<div class="pd-review-imgs">${r.images.map(u => `<img src="${u}" alt="" onclick="window.open(this.src)">`).join('')}</div>`
        : '';
    const reply = r.merchantReply
        ? `<div class="pd-review-reply"><b>商家回复：</b>${escapeHtml(r.merchantReply)}</div>`
        : '';
    return `
        <div class="pd-review-item">
            ${avatar}
            <div class="pd-review-main">
                <div class="pd-review-top">
                    <span class="pd-review-name">${escapeHtml(r.userName || '')}</span>
                    <span class="pd-review-stars">${pdStars(r.rating)}</span>
                </div>
                <div class="pd-review-content">${escapeHtml(r.content || '')}</div>
                ${images}
                <div class="pd-review-time">${r.createTime || ''}</div>
                ${reply}
            </div>
        </div>
    `;
}

async function initProductReviews(productId) {
    pdReviewProductId = productId;
    pdReviewPage = 1;
    const listBox = document.getElementById('pdReviewList');
    const avgEl = document.getElementById('pdReviewAvg');
    const moreBtn = document.getElementById('pdReviewMore');
    if (!listBox) return;

    try {
        const [sumJson, listJson] = await Promise.all([
            request(`/api/product/review-summary?productId=${productId}`),
            request(`/api/product/reviews?productId=${productId}&page=1&size=5`)
        ]);
        const sum = sumJson.data || { avg: 5, count: 0 };
        if (avgEl) avgEl.textContent = `${Number(sum.avg || 5).toFixed(1)} 分 · ${sum.count || 0} 条评价`;

        const data = listJson.data || {};
        const list = data.list || [];
        pdReviewPages = data.pages || 1;
        listBox.innerHTML = list.length
            ? list.map(renderPdReview).join('')
            : '<div class="empty-tip">还没有评价，购买后可以来评价</div>';

        if (moreBtn) {
            moreBtn.style.display = pdReviewPage < pdReviewPages ? '' : 'none';
            moreBtn.onclick = loadMorePdReviews;
        }
    } catch (e) {
        console.error('[product] 评价加载失败：', e);
        listBox.innerHTML = '<div class="empty-tip">评价加载失败</div>';
    }
}

async function loadMorePdReviews() {
    if (!pdReviewProductId || pdReviewPage >= pdReviewPages) return;
    const moreBtn = document.getElementById('pdReviewMore');
    if (moreBtn) moreBtn.disabled = true;
    try {
        pdReviewPage++;
        const json = await request(`/api/product/reviews?productId=${pdReviewProductId}&page=${pdReviewPage}&size=5`);
        const data = json.data || {};
        const list = data.list || [];
        pdReviewPages = data.pages || pdReviewPages;
        const box = document.getElementById('pdReviewList');
        if (box && list.length) {
            box.insertAdjacentHTML('beforeend', list.map(renderPdReview).join(''));
        }
        if (moreBtn) {
            moreBtn.disabled = false;
            moreBtn.style.display = pdReviewPage < pdReviewPages ? '' : 'none';
        }
    } catch (e) {
        pdReviewPage--;
        if (moreBtn) moreBtn.disabled = false;
    }
}

async function initProductMerchant(merchantId) {
    if (!merchantId) return;
    const section = document.getElementById('pdMerchantSection');
    if (!section) return;
    try {
        const json = await request(`/api/product/merchant/${merchantId}`);
        const m = json.data;
        if (!m) return;
        section.style.display = '';
        section.innerHTML = `
            <h3 class="pd-section-title">商家信息</h3>
            <div class="pd-merchant-card">
                ${m.logo
                    ? `<img class="pd-merchant-logo" src="${m.logo}" alt="">`
                    : `<div class="pd-merchant-logo pd-avatar-text">${escapeHtml((m.shopName || '店').charAt(0))}</div>`}
                <div class="pd-merchant-info">
                    <div class="pd-merchant-name">${escapeHtml(m.shopName || '')}</div>
                    <div class="pd-merchant-desc">${escapeHtml(m.description || '')}</div>
                    <div class="pd-merchant-count">在售商品 ${m.productCount || 0} 件</div>
                </div>
                <button class="pd-merchant-enter" type="button" id="pdEnterMerchant">进入商家</button>
            </div>
        `;
        document.getElementById('pdEnterMerchant').onclick = () => {
            location.hash = '#/merchant-shop?id=' + m.id;
        };
    } catch (e) {
        console.error('[product] 商家信息加载失败：', e);
    }
}

// ========== 注册到路由 ==========
registerPageHook('product', async () => {
    await loadProductDetail();
});