// js/pages/shop.js
// 商城首页（Apple Store 风格 + 后端数据）

// ========== 分类图标映射 ==========
const CATEGORY_ICONS = {
    '专辑CD': '💿',
    '明星周边': '🎤',
    '二次元周边': '🎮',
    '签名照': '📸',
    '虚拟商品': '🎫'
};

// ========== 状态 ==========
const shopState = {
    page: 1,
    size: 12,
    categoryId: '',
    keyword: ''
};

let shopEventsBound = false;

// ========== 加载分类 ==========
async function loadShopCategories() {
    const nav = document.getElementById('shopCategoryNav');
    if (!nav) return;

    try {
        const json = await request('/api/product/category/list');
        if (!json.success) return;

        nav.innerHTML = '';

        // "全部"分类
        const all = document.createElement('div');
        all.className = 'shop-category-item active';
        all.dataset.category = '';
        all.innerHTML = `
            <div class="icon-circle">🏠</div>
            <span>全部</span>
        `;
        all.onclick = () => selectShopCategory(all, '');
        nav.appendChild(all);

        // 数据库里的分类
        json.data.forEach(cat => {
            const div = document.createElement('div');
            div.className = 'shop-category-item';
            div.dataset.category = cat.id;
            div.innerHTML = `
                <div class="icon-circle">${CATEGORY_ICONS[cat.name] || '🛍️'}</div>
                <span>${cat.name}</span>
            `;
            div.onclick = () => selectShopCategory(div, cat.id);
            nav.appendChild(div);
        });
    } catch (err) {
        console.error('[shop] 加载分类失败：', err);
    }
}

// ========== 切换分类 ==========
function selectShopCategory(el, categoryId) {
    document.querySelectorAll('#shopCategoryNav .shop-category-item').forEach(item => {
        item.classList.remove('active');
    });
    el.classList.add('active');
    shopState.categoryId = categoryId;
    shopState.page = 1;
    loadProductList();
}

// ========== 加载商品列表 ==========
async function loadProductList() {
    const grid = document.getElementById('productGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;text-align:center;padding:60px 0;color:#86868b;">加载中...</div>';

    try {
        const params = new URLSearchParams({
            page: shopState.page,
            size: shopState.size
        });
        if (shopState.categoryId) params.append('categoryId', shopState.categoryId);
        if (shopState.keyword) params.append('keyword', shopState.keyword);

        const json = await request(`/api/product/list?${params.toString()}`);
        if (!json.success) {
            grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;text-align:center;padding:60px 0;color:#86868b;">加载失败</div>';
            return;
        }

        const pageData = json.data;
        const list = pageData.records || [];

        if (list.length === 0) {
            grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;text-align:center;padding:60px 0;color:#86868b;">没有找到相关商品</div>';
            renderPagination(pageData);
            return;
        }

        grid.innerHTML = '';
        list.forEach((p, idx) => {
            const card = document.createElement('div');
            card.className = 'shop-card reveal';
            // 错落动画：每张卡片延迟 50ms 出
            card.style.transitionDelay = `${idx * 60}ms`;
            card.onclick = () => {
                location.hash = `#/product?id=${p.id}`;
            };

            const isVirtual = p.productType === 'virtual';
            const tagClass = isVirtual ? 'virtual' : 'physical';
            const tagText = isVirtual ? '虚拟' : '实体';

            card.innerHTML = `
                <span class="tag ${tagClass}">${tagText}</span>
                <img class="shop-card-image" src="${p.coverUrl || ''}" alt="${escapeHtml(p.name)}" loading="lazy">
                <div class="shop-card-title">${escapeHtml(p.name)}</div>
                <div class="shop-card-subtitle">${escapeHtml(p.subtitle || '')}</div>
                <div class="shop-card-price">
                    ${p.price}
                    ${p.originalPrice ? `<span class="shop-card-original">¥${p.originalPrice}</span>` : ''}
                </div>
            `;
            grid.appendChild(card);
        });

        renderPagination(pageData);
        // 商品渲染完，重新观察一次（因为新生成的卡片也是 .reveal）
        setTimeout(() => {
            observeShopReveals();
        }, 50);
    } catch (err) {
        console.error('[shop] 加载商品失败：', err);
        grid.innerHTML = '<div class="empty-tip" style="grid-column:1/-1;text-align:center;padding:60px 0;color:#86868b;">网络异常</div>';
    }
}

// ========== 分页 ==========
function renderPagination(pageData) {
    const wrap = document.getElementById('shopPagination');
    if (!wrap) return;

    const totalPages = pageData.pages || 0;
    const current = pageData.current || 1;
    if (totalPages <= 1) {
        wrap.innerHTML = '';
        return;
    }

    wrap.innerHTML = '';

    const prev = document.createElement('button');
    prev.innerText = '上一页';
    prev.disabled = current <= 1;
    prev.onclick = () => {
        if (shopState.page > 1) {
            shopState.page--;
            loadProductList();
        }
    };
    wrap.appendChild(prev);

    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.innerText = i;
        if (i === current) btn.classList.add('active');
        btn.onclick = () => {
            shopState.page = i;
            loadProductList();
        };
        wrap.appendChild(btn);
    }

    const next = document.createElement('button');
    next.innerText = '下一页';
    next.disabled = current >= totalPages;
    next.onclick = () => {
        if (shopState.page < totalPages) {
            shopState.page++;
            loadProductList();
        }
    };
    wrap.appendChild(next);
}

// ========== 滚动显现 ==========
// ================================================================
// 滚动显现动画：用 IntersectionObserver 监听 .reveal 元素
// ================================================================
let shopRevealObserver = null;

/**
 * 观察 #view-shop 里所有未激活的 .reveal 元素
 * 元素进入视口时，加 .active 类，触发 CSS 过渡
 */
function observeShopReveals() {
    // 不支持 IntersectionObserver 的老浏览器：直接全部显示
    if (!('IntersectionObserver' in window)) {
        document.querySelectorAll('#view-shop .reveal').forEach(el => el.classList.add('active'));
        return;
    }

    // 单例：只创建一次 observer
    if (!shopRevealObserver) {
        shopRevealObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('active');
                    // 触发一次后取消观察，节省性能
                    shopRevealObserver.unobserve(entry.target);
                }
            });
        }, {
            root: null,
            rootMargin: '0px 0px -50px 0px',
            threshold: 0.15
        });
    }

    // 观察所有未激活的 .reveal 元素
    document.querySelectorAll('#view-shop .reveal').forEach(el => {
        if (!el.classList.contains('active')) {
            shopRevealObserver.observe(el);
        }
    });
}

// ========== 绑定事件 ==========
function bindShopEvents() {
    if (shopEventsBound) return;
    shopEventsBound = true;

    const searchBtn = document.getElementById('shop-search-btn');
    if (searchBtn) {
        searchBtn.onclick = () => {
            const input = document.getElementById('shop-search-input');
            shopState.keyword = input.value.trim();
            shopState.page = 1;
            loadProductList();
        };
    }

    const searchInput = document.getElementById('shop-search-input');
    if (searchInput) {
        searchInput.onkeydown = (e) => {
            if (e.key === 'Enter') {
                document.getElementById('shop-search-btn').click();
            }
        };
    }
}

// ========== 注册页面钩子 ==========
registerPageHook('shop', async () => {
    bindShopEvents();
    await loadShopCategories();
    await loadProductList();
    await loadCouponActivity();

    // 等 DOM 渲染完，开始观察所有 .reveal 元素
    setTimeout(() => {
        observeShopReveals();
    }, 100);
});

// ======================= 优惠券轮播（商城页） =======================
let couponCarouselTimer = null;

async function loadCouponActivity() {
    const viewport = document.getElementById('couponViewport');
    const track = document.getElementById('couponTrack');
    if (!viewport || !track) return;
    try {
        const json = await request('/api/coupon/activity');
        const data = json.data || {};
        const list = data.list || [];

        const tip = document.getElementById('couponCenterTip');
        if (tip) tip.innerText = data.logged ? '每券限领一次，领完即止' : '登录后即可领取';

        if (list.length === 0) {
            track.innerHTML = '<div class="coupon-empty">暂无进行中的优惠券活动</div>';
            stopCouponCarousel();
            return;
        }
        track.innerHTML = '';
        list.forEach(c => track.appendChild(buildCouponCard(c, !!data.logged, loadCouponActivity)));
        startCouponCountdown('#couponCenter');
        initCouponCarousel(list.length);
    } catch (e) {
        console.error('[shop] 加载优惠券活动失败：', e);
        track.innerHTML = '<div class="coupon-empty">活动加载失败</div>';
        stopCouponCarousel();
    }
}

/** 初始化轮播：一张券一屏，左右箭头 + 圆点 + 自动轮播 */
function initCouponCarousel(total) {
    const viewport = document.getElementById('couponViewport');
    const track = document.getElementById('couponTrack');
    const prevBtn = document.getElementById('couponPrev');
    const nextBtn = document.getElementById('couponNext');
    const dotsWrap = document.getElementById('couponDots');
    if (!viewport || !track) return;

    // 一张券占一屏
    const pages = Math.max(1, total);
    let current = 0;

    // 生成圆点
    if (dotsWrap) {
        dotsWrap.innerHTML = '';
        for (let i = 0; i < pages; i++) {
            const d = document.createElement('span');
            d.className = 'coupon-dot' + (i === 0 ? ' active' : '');
            d.onclick = () => goTo(i);
            dotsWrap.appendChild(d);
        }
    }

    const goTo = (idx) => {
        current = (idx + pages) % pages;
        track.style.transform = `translateX(-${current * viewport.clientWidth}px)`;
        if (dotsWrap) {
            dotsWrap.querySelectorAll('.coupon-dot').forEach((d, i) => {
                d.classList.toggle('active', i === current);
            });
        }
    };

    if (prevBtn) prevBtn.onclick = () => goTo(current - 1);
    if (nextBtn) nextBtn.onclick = () => goTo(current + 1);

    // 自动轮播：6 秒一张，不要太快；鼠标悬停暂停
    const AUTO_DELAY = 6000;
    const play = () => {
        stopCouponCarousel();
        couponCarouselTimer = setInterval(() => goTo(current + 1), AUTO_DELAY);
    };
    stopCouponCarousel();
    if (pages > 1) {
        play();
        viewport.onmouseenter = stopCouponCarousel;
        viewport.onmouseleave = play;
    }
}

function stopCouponCarousel() {
    if (couponCarouselTimer) {
        clearInterval(couponCarouselTimer);
        couponCarouselTimer = null;
    }
}
