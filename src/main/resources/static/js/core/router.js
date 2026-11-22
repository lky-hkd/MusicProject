// js/core/router.js

const ROUTES = ['home', 'user', 'collect', 'history',
    'login', 'register', 'player', 'shop', 'product', 'cart','address',
    'order-confirm', 'order-pay', 'my-orders', 'order-detail', 'coupon',
    'merchant-apply', 'merchant-center', 'activity', 'messages', 'chat', 'follow', 'feed', 'post', 'merchant-shop', 'profile-edit', 'sign'
];

const PAGE_HOOKS = {
    home: [], user: [], collect: [], history: [], login: [],
    register: [], player: [], shop: [], product: [], cart: [],
    address:[],'order-confirm': [], 'order-pay': [], 'my-orders': [],
    'order-detail': [], coupon: [], 'merchant-apply': [], 'merchant-center': [],
    activity: [], messages: [], chat: [], follow: [], feed: [], post: [], 'merchant-shop': [], 'profile-edit': [], sign: []
};

function registerPageHook(pageName, fn) {
    if (PAGE_HOOKS[pageName]) PAGE_HOOKS[pageName].push(fn);
}

const LEAVE_DURATION = 200;

let currentPage = null;
let currentHash = null;   //  新增：记录完整 hash，用于判断"同一页不同参数"

function router() {
    // 1. 从 hash 里提取路由名（去掉 #/ 和 ?参数）
    const raw = location.hash.replace('#/', '') || 'home';
    const routeName = raw.split('?')[0];
    const page = ROUTES.includes(routeName) ? routeName : 'home';

    // 2. 关键：同页 + 同参数才跳过；同页不同参数（如 #/product?id=3 → #/product?id=5）要刷新
    const fullHash = location.hash;
    if (currentPage === page && currentHash === fullHash) {
        console.log('[router] 已经是同一页且同参数，跳过');
        return;
    }

    console.log('[router] 切换到：', page, '| hash:', fullHash);

    const currentView = currentPage
        ? document.getElementById('view-' + currentPage)
        : null;
    const nextView = document.getElementById('view-' + page);

    const doSwitch = () => {
        document.querySelectorAll('.view').forEach(v => {
            v.classList.remove('active', 'leaving');
        });

        if (nextView) nextView.classList.add('active');

        // 触发页面钩子（每次切换都执行，包括同页面不同参数）
        const hooks = PAGE_HOOKS[page] || [];
        hooks.forEach(fn => {
            try {
                fn();
            } catch (err) {
                console.error(`[router] 页面 ${page} 钩子执行失败：`, err);
            }
        });

        window.scrollTo(0, 0);

        currentPage = page;
        currentHash = fullHash;   // ← 记录完整 hash
    };

    if (currentView && currentView !== nextView) {
        currentView.classList.add('leaving');
        setTimeout(doSwitch, LEAVE_DURATION);
    } else {
        doSwitch();
    }
}

registerPageHook('player', () => {
    if (typeof PlayerUI !== 'undefined') {
        PlayerUI.syncUIFromState && PlayerUI.syncUIFromState();
    }
});

window.addEventListener('hashchange', router);

window.addEventListener('load', () => {
    if (!location.hash || location.hash === '#' || location.hash === '#/') {
        location.hash = '#/home';
    } else {
        router();
    }
});