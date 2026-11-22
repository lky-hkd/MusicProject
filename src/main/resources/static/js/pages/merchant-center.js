// js/pages/merchant-center.js
// 商家中心：看板/商品/订单/退款/店铺
// 结构对齐 index.html（tab: dashboard/products/orders/refunds/shop，弹窗: #mcModal）

const MC = {
    tab: 'dashboard',
    productPage: 1, orderPage: 1, refundPage: 1, reviewPage: 1,
    productKeyword: '', productStatus: '',
    orderStatus: '', orderKeyword: '',
    refundStatus: '',
    replyReviewId: null,
    chartType: 'line', chartRange: 7, chartData: null,
    initialized: false
};

const MC_ORDER_STATUS = { 0:'待付款',1:'已付款',2:'已发货',3:'已完成',4:'已取消',5:'已退款' };
const MC_REFUND_STATUS = { 1:'申请中',2:'退款中',3:'退款成功',4:'退款失败',5:'已拒绝' };
const orderStatusText  = s => MC_ORDER_STATUS[s] || '未知';
const refundStatusText = s => MC_REFUND_STATUS[s] || '未知';
const productStatusText = s => s === 1 ? '上架中' : (s === 0 ? '已下架' : '未知');

// 统一 POST
async function mcPost(url, body) {
    const json = await request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {})
    });
    if (!json.success) throw new Error(json.msg || '请求失败');
    return json.data;
}
function mcError(e) { console.error(e); alert(e.message || '操作失败'); }

// tab -> 面板 id / 加载器（严格对齐 index.html 的带 s 命名）
const MC_TABS = {
    dashboard: { el: 'mc-dashboard', load: () => loadDashboard() },
    products:  { el: 'mc-products',  load: () => loadProducts() },
    orders:    { el: 'mc-orders',    load: () => loadOrders() },
    refunds:   { el: 'mc-refunds',   load: () => loadRefunds() },
    reviews:   { el: 'mc-reviews',   load: () => loadReviews() },
    shop:      { el: 'mc-shop',      load: () => loadShop() }
};

registerPageHook('merchant-center', () => {
    const root = document.getElementById('view-merchant-center');
    if (!root) return;
    if (!MC.initialized) {
        bindMerchantCenterEvents(root);
        MC.initialized = true;
    }
    loadShopName();
    switchTab('dashboard');
});

async function loadShopName() {
    try {
        const m = await mcPost('/api/merchant/shop/info');
        const el = document.getElementById('mcShopName');
        if (el) el.textContent = m?.shopName || '';
    } catch (_) { /* 忽略，不阻塞 */ }
}

function switchTab(tab) {
    if (!MC_TABS[tab]) tab = 'dashboard';
    MC.tab = tab;
    document.querySelectorAll('.mc-tab').forEach(b =>
        b.classList.toggle('active', b.dataset.tab === tab));
    Object.entries(MC_TABS).forEach(([k, v]) => {
        const p = document.getElementById(v.el);
        if (p) p.style.display = (k === tab) ? '' : 'none';
    });
    MC_TABS[tab].load();
}

// ============ 弹窗（对齐 #mcModal 结构）============
function mcOpenModal(title, bodyHtml, footerHtml) {
    document.getElementById('mcModalTitle').innerHTML = title;
    document.getElementById('mcModalBody').innerHTML = bodyHtml;
    document.getElementById('mcModalFooter').innerHTML = footerHtml || '';
    document.getElementById('mcModal').style.display = 'flex';
}
function mcCloseModal() {
    const m = document.getElementById('mcModal');
    if (m) m.style.display = 'none';
}

// ============ 事件委托（一次性）============
function bindMerchantCenterEvents(root) {
    // 关闭按钮
    document.getElementById('mcModalClose')?.addEventListener('click', mcCloseModal);
    document.getElementById('mcModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'mcModal') mcCloseModal();
    });

    root.addEventListener('click', (e) => {
        const t = e.target;
        const tab = t.dataset.tab;
        if (tab && MC_TABS[tab]) { switchTab(tab); return; }

        // 商品
        if (t.id === 'mc-product-add') { openProductModal(null); return; }
        if (t.classList.contains('mc-edit-product')) { openProductModal(t.dataset.id); return; }
        if (t.classList.contains('mc-toggle-product')) { toggleProductStatus(t.dataset.id, t.dataset.status); return; }
        if (t.classList.contains('mc-del-product')) { deleteProduct(t.dataset.id); return; }
        if (t.id === 'mc-product-search') {
            MC.productKeyword = document.getElementById('mc-product-kw').value.trim();
            MC.productStatus = document.getElementById('mc-product-status').value;
            MC.productPage = 1; loadProducts(); return;
        }
        if (t.classList.contains('mc-product-prev')) { if (MC.productPage > 1) { MC.productPage--; loadProducts(); } return; }
        if (t.classList.contains('mc-product-next')) { MC.productPage++; loadProducts(); return; }

        // 订单
        if (t.classList.contains('mc-order-detail')) { openOrderDetail(t.dataset.no); return; }
        if (t.classList.contains('mc-order-ship')) { openShipModal(t.dataset.no); return; }
        if (t.id === 'mc-order-search') {
            MC.orderKeyword = document.getElementById('mc-order-kw').value.trim();
            MC.orderStatus = document.getElementById('mc-order-status').value;
            MC.orderPage = 1; loadOrders(); return;
        }
        if (t.classList.contains('mc-order-prev')) { if (MC.orderPage > 1) { MC.orderPage--; loadOrders(); } return; }
        if (t.classList.contains('mc-order-next')) { MC.orderPage++; loadOrders(); return; }

        // 退款
        if (t.classList.contains('mc-refund-review')) { openRefundModal(t.dataset.id); return; }
        if (t.id === 'mc-refund-search') {
            MC.refundStatus = document.getElementById('mc-refund-status').value;
            MC.refundPage = 1; loadRefunds(); return;
        }
        if (t.classList.contains('mc-refund-prev')) { if (MC.refundPage > 1) { MC.refundPage--; loadRefunds(); } return; }
        if (t.classList.contains('mc-refund-next')) { MC.refundPage++; loadRefunds(); return; }

        // 评价
        if (t.classList.contains('mc-reply-review')) { openReplyModal(t.dataset.id); return; }
        if (t.classList.contains('mc-review-prev')) { if (MC.reviewPage > 1) { MC.reviewPage--; loadReviews(); } return; }
        if (t.classList.contains('mc-review-next')) { MC.reviewPage++; loadReviews(); return; }
        if (t.id === 'mc-reply-save') { submitReply(); return; }

        // 商品保存按钮（弹窗 footer 里的按钮用 id 触发）
        if (t.id === 'mc-product-save') { document.getElementById('mc-product-form').requestSubmit(); return; }
        if (t.id === 'mc-ship-save')   { document.getElementById('mc-ship-form').requestSubmit(); return; }
        if (t.id === 'mc-refund-save') { document.getElementById('mc-refund-form').requestSubmit(); return; }
        if (t.id === 'mc-modal-cancel') { mcCloseModal(); return; }

        // 店铺
        if (t.id === 'mc-shop-save') { saveShop(); return; }
    });

    root.addEventListener('submit', (e) => {
        e.preventDefault();
        if (e.target.id === 'mc-product-form') submitProduct(e.target);
        else if (e.target.id === 'mc-ship-form') submitShip(e.target);
        else if (e.target.id === 'mc-refund-form') submitRefund(e.target);
    });

    root.addEventListener('change', (e) => {
        if (e.target.id === 'f-productType') {
            const v = document.getElementById('f-virtual-wrap');
            if (v) v.style.display = (e.target.value === 'virtual') ? '' : 'none';
        }
        if (e.target.id === 'f-refund-approve') {
            const w = document.getElementById('f-reject-wrap');
            if (w) w.style.display = (e.target.value === 'false') ? '' : 'none';
        }
    });
}

// ============ 看板 ============
async function loadDashboard() {
    const el = document.getElementById('mc-dashboard');
    if (mcChartInstance) {
        mcChartInstance.dispose();
        mcChartInstance = null;
    }
    el.innerHTML = '加载中...';
    try {
        const d = await mcPost('/api/merchant/dashboard/summary');
        el.innerHTML = `
      <div class="mc-cards">
        <div class="mc-card"><div class="mc-card-num">${d.totalProducts ?? 0}</div><div class="mc-card-label">商品总数</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.onSaleProducts ?? 0}</div><div class="mc-card-label">在售商品</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.pendingShip ?? 0}</div><div class="mc-card-label">待发货</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.pendingRefund ?? 0}</div><div class="mc-card-label">待处理退款</div></div>
        <div class="mc-card"><div class="mc-card-num">¥${d.totalSales || 0}</div><div class="mc-card-label">累计销售额</div></div>
      </div>

      <div class="mc-chart-panel">
        <div class="mc-chart-head">
          <div class="mc-chart-types">
            <button type="button" class="mc-chart-type active" data-chart="line">折线</button>
            <button type="button" class="mc-chart-type" data-chart="bar">柱状</button>
            <button type="button" class="mc-chart-type" data-chart="pie">饼图</button>
          </div>
          <div class="mc-chart-ops">
            <select id="mcChartRange">
              <option value="7">近 7 天</option>
              <option value="30">近 30 天</option>
            </select>
            <button type="button" id="mcChartRefresh">刷新</button>
          </div>
        </div>
        <div id="mcSalesChart" class="mc-chart"></div>
        <div class="mc-chart-foot" id="mcChartFoot">加载中...</div>
      </div>`;
        await initSalesChart();
    } catch (e) { mcError(e); el.innerHTML = '加载失败'; }
}

// ============ 销售统计图 ============
let mcChartInstance = null;
let mcChartLoading = false;
let mcChartResizeBound = false;

async function initSalesChart() {
    const box = document.getElementById('mcSalesChart');
    if (!box) return;
    if (typeof echarts === 'undefined') {
        box.innerHTML = '<div class="mc-chart-empty">图表组件加载失败</div>';
        return;
    }
    if (mcChartInstance) {
        mcChartInstance.dispose();
        mcChartInstance = null;
    }
    mcChartInstance = echarts.init(box);
    if (!mcChartResizeBound) {
        mcChartResizeBound = true;
        window.addEventListener('resize', () => {
            if (mcChartInstance) mcChartInstance.resize();
        });
    }

    const typeBtns = document.querySelectorAll('.mc-chart-type');
    typeBtns.forEach(btn => {
        btn.classList.toggle('active', btn.dataset.chart === MC.chartType);
        btn.onclick = () => {
            MC.chartType = btn.dataset.chart;
            typeBtns.forEach(b => b.classList.toggle('active', b === btn));
            renderSalesChart();
        };
    });

    const rangeSel = document.getElementById('mcChartRange');
    if (rangeSel) {
        rangeSel.value = String(MC.chartRange);
        rangeSel.onchange = () => {
            MC.chartRange = Number(rangeSel.value);
            loadChartData(false);
        };
    }
    const refreshBtn = document.getElementById('mcChartRefresh');
    if (refreshBtn) refreshBtn.onclick = () => loadChartData(true);

    await loadChartData(false);
}

async function loadChartData(force) {
    if (mcChartLoading) return;
    mcChartLoading = true;
    const btn = document.getElementById('mcChartRefresh');
    const foot = document.getElementById('mcChartFoot');
    if (btn) { btn.disabled = true; btn.textContent = '刷新中...'; }
    if (foot) foot.textContent = '加载中...';
    try {
        const d = await mcPost(`/api/merchant/dashboard/chart?range=${MC.chartRange}&refresh=${force ? 1 : 0}`);
        MC.chartData = d || {};
        renderSalesChart();
        if (foot) {
            foot.textContent = `${d.cached ? '来自缓存' : '实时计算'} · 更新于 ${d.updatedAt || '-'}`;
        }
    } catch (e) {
        mcError(e);
        if (foot) foot.textContent = '加载失败';
    } finally {
        mcChartLoading = false;
        if (btn) { btn.disabled = false; btn.textContent = '刷新'; }
    }
}

function renderSalesChart() {
    if (!mcChartInstance) return;
    const d = MC.chartData || {};
    const labels = d.labels || [];
    const sales = (d.sales || []).map(Number);

    if (MC.chartType === 'pie') {
        const items = (d.topProducts || [])
            .filter(p => Number(p.amount) > 0)
            .map(p => ({ name: p.name, value: Number(p.amount) }));
        mcChartInstance.setOption({
            backgroundColor: 'transparent',
            color: ['#4f8cff', '#22c55e', '#f59e0b', '#ef5da8', '#8b5cf6'],
            tooltip: {
                trigger: 'item',
                formatter: p => `${p.name}<br/>¥${Number(p.value).toFixed(2)}（${p.percent}%）`
            },
            legend: {
                bottom: 0,
                textStyle: { color: '#9fb0c8' },
                itemWidth: 10,
                itemHeight: 10
            },
            series: [{
                type: 'pie',
                radius: ['42%', '68%'],
                center: ['50%', '44%'],
                avoidLabelOverlap: true,
                itemStyle: { borderColor: '#0b0f16', borderWidth: 2 },
                label: { color: '#c9d6ea', formatter: '{b}\n{d}%' },
                data: items
            }],
            graphic: items.length ? [] : [{
                type: 'text',
                left: 'center',
                top: 'middle',
                style: { text: '暂无销售数据', fill: '#7d8797', fontSize: 14 }
            }]
        }, true);
        return;
    }

    const isBar = MC.chartType === 'bar';
    mcChartInstance.setOption({
        backgroundColor: 'transparent',
        grid: { left: 48, right: 18, top: 30, bottom: 34 },
        tooltip: {
            trigger: 'axis',
            formatter: params => {
                const p = params[0];
                return `${p.axisValue}<br/>销售额 ¥${Number(p.value).toFixed(2)}`;
            }
        },
        xAxis: {
            type: 'category',
            data: labels,
            axisTick: { show: false },
            axisLine: { lineStyle: { color: 'rgba(255,255,255,.18)' } },
            axisLabel: { color: '#8fa0b8' }
        },
        yAxis: {
            type: 'value',
            splitLine: { lineStyle: { color: 'rgba(255,255,255,.06)' } },
            axisLabel: { color: '#8fa0b8' }
        },
        series: [{
            type: isBar ? 'bar' : 'line',
            data: sales,
            smooth: !isBar,
            symbol: 'circle',
            symbolSize: 6,
            barMaxWidth: 26,
            itemStyle: { color: '#4f8cff', borderRadius: isBar ? [6, 6, 0, 0] : 0 },
            lineStyle: { width: 2.5, color: '#4f8cff' },
            areaStyle: isBar ? undefined : {
                color: {
                    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [
                        { offset: 0, color: 'rgba(79,140,255,.35)' },
                        { offset: 1, color: 'rgba(79,140,255,0)' }
                    ]
                }
            }
        }]
    }, true);
}

// 通用分页
function mcPager(prefix, total, page, size) {
    const totalPages = Math.max(1, Math.ceil(total / size));
    return `
      <div class="mc-pagination">
        <button class="${prefix}-prev" ${page <= 1 ? 'disabled' : ''}>上一页</button>
        <span>第 ${page} / ${totalPages} 页（共 ${total} 条）</span>
        <button class="${prefix}-next" ${page >= totalPages ? 'disabled' : ''}>下一页</button>
      </div>`;
}
// （接上文）以下渲染/交互函数逻辑同前，差异点见注释

async function loadProducts() {
    const el = document.getElementById('mc-products');
    el.innerHTML = `
    <div class="mc-toolbar">
      <input id="mc-product-kw" placeholder="商品名称" value="${escapeHtml(MC.productKeyword)}">
      <select id="mc-product-status">
        <option value="">全部状态</option>
        <option value="1" ${MC.productStatus === '1' ? 'selected' : ''}>上架中</option>
        <option value="0" ${MC.productStatus === '0' ? 'selected' : ''}>已下架</option>
      </select>
      <button id="mc-product-search">搜索</button>
      <button id="mc-product-add" class="mc-primary">+ 新增商品</button>
    </div>
    <div id="mc-product-list">加载中...</div>`;
    try {
        const pageData = await mcPost('/api/merchant/product/list', {
            page: MC.productPage, size: 10,
            keyword: MC.productKeyword || null,
            status: MC.productStatus ? Number(MC.productStatus) : null
        });
        const list = pageData.records || [];
        const total = Number(pageData.total || 0);
        let html = `<table class="mc-table"><thead><tr>
        <th>ID</th><th>名称</th><th>类型</th><th>价格</th>
        <th>库存</th><th>状态</th><th>销量</th><th>操作</th>
      </tr></thead><tbody>`;
        if (!list.length) html += `<tr><td colspan="8">暂无商品</td></tr>`;
        list.forEach(p => {
            const next = p.status === 1 ? 0 : 1;
            html += `<tr>
        <td>${p.id}</td>
        <td>${escapeHtml(p.name)}</td>
        <td>${p.productType === 'virtual' ? '虚拟' : '实物'}</td>
        <td>¥${p.price}</td>
        <td>${p.stock}</td>
        <td>${productStatusText(p.status)}</td>
        <td>${p.sales || 0}</td>
        <td>
          <button class="mc-edit-product" data-id="${p.id}">编辑</button>
          <button class="mc-toggle-product" data-id="${p.id}" data-status="${next}">${p.status === 1 ? '下架' : '上架'}</button>
          <button class="mc-del-product del-btn" data-id="${p.id}" title="删除">${DEL_ICON}</button>
        </td></tr>`;
        });
        html += `</tbody></table>` + mcPager('mc-product', total, MC.productPage, 10);
        document.getElementById('mc-product-list').innerHTML = html;
    } catch (e) {
        mcError(e);
        document.getElementById('mc-product-list').innerHTML = '加载失败';
    }
}

async function openProductModal(id) {
    let p = { id:null,name:'',subtitle:'',description:'',productType:'physical',
        categoryId:null,virtualType:'',virtualValue:null,price:'',originalPrice:'',
        coverUrl:'',images:'',stock:0,status:0 };
    if (id) {
        try { p = await mcPost('/api/merchant/product/detail', { id: Number(id) }); }
        catch (e) { mcError(e); return; }
    }
    const isEdit = !!p.id;
    const body = `
      <form id="mc-product-form" class="mc-form">
        <input type="hidden" name="id" value="${p.id || ''}">
        <label>名称<input name="name" required value="${escapeHtml(p.name)}"></label>
        <label>副标题<input name="subtitle" value="${escapeHtml(p.subtitle || '')}"></label>
        <label>描述<textarea name="description">${escapeHtml(p.description || '')}</textarea></label>
        <label>类型
          <select name="productType" id="f-productType">
            <option value="physical" ${p.productType === 'physical' ? 'selected' : ''}>实物</option>
            <option value="virtual" ${p.productType === 'virtual' ? 'selected' : ''}>虚拟</option>
          </select>
        </label>
        <div id="f-virtual-wrap" style="display:${p.productType === 'virtual' ? '' : 'none'}">
          <label>虚拟类型<input name="virtualType" value="${escapeHtml(p.virtualType || '')}"></label>
          <label>虚拟值<input name="virtualValue" type="number" value="${p.virtualValue ?? ''}"></label>
        </div>
        <label>分类ID<input name="categoryId" type="number" value="${p.categoryId ?? ''}"></label>
        <label>价格<input name="price" type="number" step="0.01" required value="${p.price ?? ''}"></label>
        <label>原价<input name="originalPrice" type="number" step="0.01" value="${p.originalPrice ?? ''}"></label>
        <label>封面URL<input name="coverUrl" value="${escapeHtml(p.coverUrl || '')}"></label>
        <label>图片(JSON)<input name="images" value="${escapeHtml(p.images || '')}"></label>
        <label>库存<input name="stock" type="number" required value="${p.stock ?? 0}"></label>
        <label>状态
          <select name="status">
            <option value="0" ${p.status === 0 ? 'selected' : ''}>下架</option>
            <option value="1" ${p.status === 1 ? 'selected' : ''}>上架</option>
          </select>
        </label>
      </form>`;
    const footer = `<button type="button" id="mc-modal-cancel">取消</button>
      <button type="button" id="mc-product-save" class="mc-primary">保存</button>`;
    mcOpenModal(isEdit ? '编辑商品' : '新增商品', body, footer);
}

async function submitProduct(form) {
    const fd = new FormData(form);
    const num = (k) => fd.get(k) !== '' && fd.get(k) != null ? Number(fd.get(k)) : null;
    const str = (k) => (fd.get(k) || '').trim() || null;
    const body = {
        id: fd.get('id') ? Number(fd.get('id')) : null,
        name: str('name'),
        subtitle: str('subtitle'),
        description: str('description'),
        productType: fd.get('productType'),
        categoryId: num('categoryId'),
        virtualType: str('virtualType'),
        virtualValue: num('virtualValue'),
        price: num('price'),
        originalPrice: num('originalPrice'),
        coverUrl: str('coverUrl'),
        images: str('images'),
        stock: fd.get('stock') !== '' ? Number(fd.get('stock')) : 0,
        status: Number(fd.get('status'))
    };
    try {
        await mcPost('/api/merchant/product/save', body);
        mcCloseModal();
        loadProducts();
    } catch (e) { mcError(e); }
}

async function toggleProductStatus(id, status) {
    try {
        await mcPost('/api/merchant/product/status', { id: Number(id), status: Number(status) });
        loadProducts();
    } catch (e) { mcError(e); }
}

async function deleteProduct(id) {
    if (!confirm('确定删除该商品？已产生订单的商品无法删除，只能下架。')) return;
    try {
        await mcPost('/api/merchant/product/delete', { id: Number(id) });
        loadProducts();
    } catch (e) { mcError(e); }
}

// ============ 订单 ============
async function loadOrders() {
    const el = document.getElementById('mc-orders');
    el.innerHTML = `
    <div class="mc-toolbar">
      <input id="mc-order-kw" placeholder="订单号 / 收货人" value="${escapeHtml(MC.orderKeyword)}">
      <select id="mc-order-status">
        <option value="">全部状态</option>
        ${[0,1,2,3,4,5].map(s =>
        `<option value="${s}" ${MC.orderStatus === String(s) ? 'selected' : ''}>${orderStatusText(s)}</option>`
    ).join('')}
      </select>
      <button id="mc-order-search">搜索</button>
    </div>
    <div id="mc-order-list">加载中...</div>`;
    try {
        const pageData = await mcPost('/api/merchant/order/list', {
            page: MC.orderPage, size: 10,
            keyword: MC.orderKeyword || null,
            status: MC.orderStatus !== '' ? Number(MC.orderStatus) : null
        });
        const records = pageData.records || [];
        const total = Number(pageData.total || 0);
        let html = `<table class="mc-table"><thead><tr>
        <th>订单号</th><th>收货人</th><th>金额</th><th>状态</th><th>下单时间</th><th>操作</th>
      </tr></thead><tbody>`;
        if (!records.length) html += `<tr><td colspan="6">暂无订单</td></tr>`;
        records.forEach(r => {
            const o = r.order || {};
            html += `<tr>
        <td>${escapeHtml(o.orderNo)}</td>
        <td>${escapeHtml(o.receiver || '-')}</td>
        <td>¥${o.payAmount ?? 0}</td>
        <td>${orderStatusText(o.status)}</td>
        <td>${o.createTime ? escapeHtml(String(o.createTime).replace('T', ' ')) : '-'}</td>
        <td>
          <button class="mc-order-detail" data-no="${escapeHtml(o.orderNo)}">详情</button>
          ${o.status === 1 ? `<button class="mc-order-ship" data-no="${escapeHtml(o.orderNo)}">发货</button>` : ''}
        </td></tr>`;
        });
        html += `</tbody></table>` + mcPager('mc-order', total, MC.orderPage, 10);
        document.getElementById('mc-order-list').innerHTML = html;
    } catch (e) {
        mcError(e);
        document.getElementById('mc-order-list').innerHTML = '加载失败';
    }
}

async function openOrderDetail(orderNo) {
    try {
        const data = await mcPost('/api/merchant/order/detail', { orderNo });
        const o = data.order || {};
        const items = data.items || [];
        const rows = items.map(i => `<tr>
        <td>${escapeHtml(i.productName)}</td>
        <td>${i.productType === 'virtual' ? '虚拟' : '实物'}</td>
        <td>¥${i.price}</td>
        <td>${i.quantity}</td>
        <td>¥${i.subtotal}</td>
      </tr>`).join('');
        const body = `
          <div class="mc-detail">
            <p>订单号：${escapeHtml(o.orderNo)}</p>
            <p>状态：${orderStatusText(o.status)}</p>
            <p>收货人：${escapeHtml(o.receiver || '-')}　${escapeHtml(o.phone || '')}</p>
            <p>地址：${escapeHtml(o.addressDetail || '-')}</p>
            <p>实付：¥${o.payAmount ?? 0}</p>
            <p>快递单号：${escapeHtml(o.expressNo || '-')}</p>
          </div>
          <table class="mc-table">
            <thead><tr><th>商品</th><th>类型</th><th>单价</th><th>数量</th><th>小计</th></tr></thead>
            <tbody>${rows || '<tr><td colspan="5">无明细</td></tr>'}</tbody>
          </table>`;
        mcOpenModal('订单详情', body, `<button type="button" id="mc-modal-cancel">关闭</button>`);
    } catch (e) { mcError(e); }
}

function openShipModal(orderNo) {
    const body = `
      <form id="mc-ship-form" class="mc-form">
        <input type="hidden" name="orderNo" value="${escapeHtml(orderNo)}">
        <label>快递单号<input name="expressNo" required></label>
      </form>`;
    const footer = `<button type="button" id="mc-modal-cancel">取消</button>
      <button type="button" id="mc-ship-save" class="mc-primary">确认发货</button>`;
    mcOpenModal('订单发货', body, footer);
}

async function submitShip(form) {
    const fd = new FormData(form);
    try {
        await mcPost('/api/merchant/order/ship', {
            orderNo: fd.get('orderNo'),
            expressNo: (fd.get('expressNo') || '').trim()
        });
        mcCloseModal();
        loadOrders();
    } catch (e) { mcError(e); }
}

// ============ 退款 ============
async function loadRefunds() {
    const el = document.getElementById('mc-refunds');
    el.innerHTML = `
    <div class="mc-toolbar">
      <select id="mc-refund-status">
        <option value="">全部状态</option>
        ${[1,2,3,4,5].map(s =>
        `<option value="${s}" ${MC.refundStatus === String(s) ? 'selected' : ''}>${refundStatusText(s)}</option>`
    ).join('')}
      </select>
      <button id="mc-refund-search">搜索</button>
    </div>
    <div id="mc-refund-list">加载中...</div>`;
    try {
        const pageData = await mcPost('/api/merchant/refund/list', {
            page: MC.refundPage, size: 10,
            status: MC.refundStatus !== '' ? Number(MC.refundStatus) : null
        });
        const list = pageData.records || [];
        const total = Number(pageData.total || 0);
        let html = `<table class="mc-table"><thead><tr>
        <th>退款单号</th><th>订单号</th><th>退款金额</th><th>原因</th><th>状态</th><th>申请时间</th><th>操作</th>
      </tr></thead><tbody>`;
        if (!list.length) html += `<tr><td colspan="7">暂无退款单</td></tr>`;
        list.forEach(r => {
            html += `<tr>
        <td>${escapeHtml(r.refundNo)}</td>
        <td>${escapeHtml(r.orderNo)}</td>
        <td>¥${r.refundAmount ?? 0}</td>
        <td>${escapeHtml(r.reason || '-')}</td>
        <td>${refundStatusText(r.status)}</td>
        <td>${r.createTime ? escapeHtml(String(r.createTime).replace('T', ' ')) : '-'}</td>
        <td>${r.status === 1 ? `<button class="mc-refund-review" data-id="${r.id}">审核</button>` : '-'}</td>
      </tr>`;
        });
        html += `</tbody></table>` + mcPager('mc-refund', total, MC.refundPage, 10);
        document.getElementById('mc-refund-list').innerHTML = html;
    } catch (e) {
        mcError(e);
        document.getElementById('mc-refund-list').innerHTML = '加载失败';
    }
}

// ============ 评价 ============
async function loadReviews() {
    const el = document.getElementById('mc-reviews');
    if (!el) return;
    el.innerHTML = '加载中...';
    try {
        const json = await request(`/api/merchant/review/list?page=${MC.reviewPage}&size=10`);
        const data = json.data || {};
        const list = data.list || [];

        let html = `<table class="mc-table"><thead><tr>
            <th>商品</th><th>评分</th><th>评价内容</th><th>回复</th>
        </tr></thead><tbody>`;
        if (!list.length) html += `<tr><td colspan="4">暂无评价</td></tr>`;
        list.forEach(r => {
            const imgs = (r.images || []).map(u => `<img src="${u}" class="mc-review-img">`).join('');
            const reply = r.merchantReply
                ? `<div class="mc-reply-text">${escapeHtml(r.merchantReply)}</div>`
                : `<button class="mc-reply-review" data-id="${r.id}">回复</button>`;
            html += `<tr>
                <td>
                    <div class="mc-review-product">
                        ${r.productCover ? `<img src="${r.productCover}" alt="">` : ''}
                        <span>${escapeHtml(r.productName || '')}</span>
                    </div>
                </td>
                <td class="mc-review-stars">${'★'.repeat(r.rating || 5)}</td>
                <td>
                    <div>${escapeHtml(r.content || '')}</div>
                    ${imgs ? `<div class="mc-review-imgs">${imgs}</div>` : ''}
                    <div class="mc-review-meta">${escapeHtml(r.userName || '')} · ${r.createTime || ''}</div>
                </td>
                <td>${reply}</td>
            </tr>`;
        });
        html += `</tbody></table>` + mcPager('mc-review', data.total || 0, MC.reviewPage, 10);
        el.innerHTML = html;
    } catch (e) {
        mcError(e);
        el.innerHTML = '加载失败';
    }
}

function openReplyModal(reviewId) {
    MC.replyReviewId = reviewId;
    mcOpenModal('回复评价',
        `<textarea id="mc-reply-content" rows="5" maxlength="500" placeholder="输入回复内容"></textarea>`,
        `<button type="button" id="mc-modal-cancel">取消</button>
         <button type="button" id="mc-reply-save">提交回复</button>`);
}

async function submitReply() {
    const content = (document.getElementById('mc-reply-content')?.value || '').trim();
    if (!content) {
        alert('请输入回复内容');
        return;
    }
    try {
        await mcPost(`/api/merchant/review/${MC.replyReviewId}/reply`, { content });
        mcCloseModal();
        loadReviews();
    } catch (e) {
        mcError(e);
    }
}

function openRefundModal(refundId) {
    const body = `
      <form id="mc-refund-form" class="mc-form">
        <input type="hidden" name="refundId" value="${refundId}">
        <label>处理结果
          <select name="approve" id="f-refund-approve">
            <option value="true">同意退款</option>
            <option value="false">拒绝退款</option>
          </select>
        </label>
        <div id="f-reject-wrap" style="display:none">
          <label>拒绝理由<input name="rejectReason"></label>
        </div>
      </form>`;
    const footer = `<button type="button" id="mc-modal-cancel">取消</button>
      <button type="button" id="mc-refund-save" class="mc-primary">提交</button>`;
    mcOpenModal('退款审核', body, footer);
}

async function submitRefund(form) {
    const fd = new FormData(form);
    const approve = fd.get('approve') === 'true';
    const rejectReason = (fd.get('rejectReason') || '').trim();
    if (!approve && !rejectReason) { alert('拒绝退款时必须填写理由'); return; }
    try {
        await mcPost('/api/merchant/refund/review', {
            refundId: Number(fd.get('refundId')),
            approve,
            rejectReason: approve ? null : rejectReason
        });
        mcCloseModal();
        loadRefunds();
    } catch (e) { mcError(e); }
}

// ============ 店铺 ============
async function loadShop() {
    const el = document.getElementById('mc-shop');
    el.innerHTML = '加载中...';
    try {
        const m = await mcPost('/api/merchant/shop/info');
        el.innerHTML = `
      <div class="mc-form">
        <label>店铺名称<input id="f-shopName" value="${escapeHtml(m.shopName || '')}"></label>
        <label>Logo<input id="f-shopLogo" value="${escapeHtml(m.logo || '')}"></label>
        <label>简介<textarea id="f-shopDesc">${escapeHtml(m.description || '')}</textarea></label>
        <label>联系电话<input id="f-shopPhone" value="${escapeHtml(m.contactPhone || '')}"></label>
        <div class="mc-form-actions">
          <button id="mc-shop-save" class="mc-primary">保存</button>
        </div>
      </div>`;
    } catch (e) { mcError(e); el.innerHTML = '加载失败'; }
}

async function saveShop() {
    const body = {
        shopName: document.getElementById('f-shopName').value.trim(),
        logo: document.getElementById('f-shopLogo').value.trim() || null,
        description: document.getElementById('f-shopDesc').value.trim() || null,
        contactPhone: document.getElementById('f-shopPhone').value.trim() || null
    };
    if (!body.shopName) { alert('店铺名称不能为空'); return; }
    try {
        await mcPost('/api/merchant/shop/update', body);
        alert('店铺信息已更新');
        document.getElementById('mcShopName').textContent = body.shopName;
        loadShop();
    } catch (e) { mcError(e); }
}