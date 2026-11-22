// js/app.js
// 应用入口：初始化播放器、绑定全局事件

document.addEventListener('DOMContentLoaded', () => {
    console.log('[app] 应用启动');

    // 统一返回按钮：透明背景 + 箭头左移放大
    const BACK_ICON = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" aria-hidden="true">'
        + '<path d="M874.690416 495.52477c0 11.2973-9.168824 20.466124-20.466124 20.466124l-604.773963 0 188.083679 188.083679c7.992021 7.992021 7.992021 20.947078 0 28.939099-4.001127 3.990894-9.240455 5.996574-14.46955 5.996574-5.239328 0-10.478655-1.995447-14.479783-5.996574l-223.00912-223.00912c-3.837398-3.837398-5.996574-9.046027-5.996574-14.46955 0-5.433756 2.159176-10.632151 5.996574-14.46955l223.019353-223.029586c7.992021-7.992021 20.957311-7.992021 28.949332 0 7.992021 8.002254 7.992021 20.957311 0 28.949332l-188.073446 188.073446 604.753497 0C865.521592 475.058646 874.690416 484.217237 874.690416 495.52477z"></path>'
        + '</svg>';
    document.querySelectorAll('.back-home, .back-user, .pp-back, #backRankBtn').forEach(el => {
        if (el.dataset.backFx) return;
        const text = (el.textContent || '').replace(/^\s*←\s*/, '').trim();
        el.dataset.backFx = '1';
        el.classList.add('back-fx');
        el.innerHTML = BACK_ICON + '<span>' + text + '</span>';
    });

    // 初始化水面（全局一次）
    if (typeof initWater === 'function') {
        initWater('canvas-container');
    }

    // 1. 初始化 AudioManager（必须第一时间）
    const audioEl = document.getElementById('globalAudio');
    if (!audioEl) {
        console.error('[app] 找不到 #globalAudio');
        return;
    }
    AudioManager.init(audioEl);

    // 2. 初始化 PlayerUI
    PlayerUI.init();

    console.log('[app] 初始化完成');
    // 刷新购物车角标
    if (typeof refreshCartBadge === 'function') {
        refreshCartBadge();
    }
});