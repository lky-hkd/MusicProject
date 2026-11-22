// js/admin/admin-merchant.js  商家模块（独立后台版）
const MC = {
  productPage: 1, orderPage: 1, refundPage: 1,
  productKeyword: '', productStatus: '',
  orderStatus: '', orderKeyword: '',
  refundStatus: ''
};
const productStatusText = s => s === 1 ? '上架中' : (s === 0 ? '已下架' : '未知');

async function renderMcDashboard() {
  const el = document.getElementById('adminContent');
  el.innerHTML = '加载中...';
  try {
    const d = await adminPost('/api/merchant/dashboard/summary');
    el.innerHTML = `
      <div class="mc-cards">
        <div class="mc-card"><div class="mc-card-num">${d.totalProducts ?? 0}</div><div class="mc-card-label">商品总数</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.onSaleProducts ?? 0}</div><div class="mc-card-label">在售商品</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.pendingShip ?? 0}</div><div class="mc-card-label">待发货</div></div>
        <div class="mc-card"><div class="mc-card-num">${d.pendingRefund ?? 0}</div><div class="mc-card-label">待处理退款</div></div>
        <div class="mc-card"><div class="mc-card-num">¥${d.totalSales || 0}</div><div class="mc-card-label">累计销售额</div></div>
      </div>`;
  } catch (e) { adminError(e); el.innerHTML = '加载失败'; }
}

// ============ 商品 ============
async function renderMcProducts() {
  const el = document.getElementById('adminContent');
  el.innerHTML = `
    <div class="mc-toolbar">
      <input id="mp-kw" placeholder="商品名称" value="${escapeHtml(MC.productKeyword)}">
      <select id="mp-status">
        <option value="">全部状态</option>
        <option value="1" ${MC.productStatus==='1'?'selected':''}>上架中</option>
        <option value="0" ${MC.productStatus==='0'?'selected':''}>已下架</option>
      </select>
      <button id="mp-search">搜索</button>
      <button id="mp-add" class="mc-primary">+ 新增商品</button>
    </div>
    <div id="mp-list">加载中...</div>`;
  document.getElementById('mp-search').onclick = () => {
    MC.productKeyword = document.getElementById('mp-kw').value.trim();
    MC.productStatus = document.getElementById('mp-status').value;
    MC.productPage = 1; loadMcProducts();
  };
  document.getElementById('mp-add').onclick = () => openMcProductModal(null);
  loadMcProducts();
}

async function loadMcProducts() {
  const listEl = document.getElementById('mp-list');
  listEl.innerHTML = '加载中...';
  try {
    const data = await adminPost('/api/merchant/product/list', {
      page: MC.productPage, size: 10,
      keyword: MC.productKeyword || null,
      status: MC.productStatus ? Number(MC.productStatus) : null
    });
    const list = data.records || [];
    const total = Number(data.total || 0);
    let html = `<table class="mc-table"><thead><tr>
      <th>ID</th><th>名称</th><th>类型</th><th>价格</th><th>库存</th><th>状态</th><th>销量</th><th>操作</th>
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
          <button class="mp-edit" data-id="${p.id}">编辑</button>
          <button class="mp-toggle" data-id="${p.id}" data-status="${next}">${p.status === 1 ? '下架' : '上架'}</button>
          <button class="mp-del del-btn" data-id="${p.id}" title="删除">${DEL_ICON}</button>
        </td></tr>`;
    });
    html += `</tbody></table>` + adminPager('mp', total, MC.productPage, 10);
    listEl.innerHTML = html;

    listEl.querySelectorAll('.mp-edit').forEach(b => b.onclick = () => openMcProductModal(b.dataset.id));
    listEl.querySelectorAll('.mp-toggle').forEach(b => b.onclick = () => toggleMcProduct(b.dataset.id, b.dataset.status));
    listEl.querySelectorAll('.mp-del').forEach(b => b.onclick = () => deleteMcProduct(b.dataset.id));
    const prev = listEl.querySelector('.mp-prev');
    const next = listEl.querySelector('.mp-next');
    if (prev) prev.onclick = () => { if (MC.productPage > 1) { MC.productPage--; loadMcProducts(); } };
    if (next) next.onclick = () => { MC.productPage++; loadMcProducts(); };
  } catch (e) { adminError(e); listEl.innerHTML = '加载失败'; }
}

async function openMcProductModal(id) {
  let p = { id:null,name:'',subtitle:'',description:'',productType:'physical',
    categoryId:null,virtualType:'',virtualValue:null,price:'',originalPrice:'',
    coverUrl:'',images:'',stock:0,status:0 };
  if (id) {
    try { p = await adminPost('/api/merchant/product/detail', { id: Number(id) }); }
    catch (e) { adminError(e); return; }
  }
  const body = `
    <form id="mp-form" class="mc-form">
      <input type="hidden" name="id" value="${p.id || ''}">
      <label>名称<input name="name" required value="${escapeHtml(p.name)}"></label>
      <label>副标题<input name="subtitle" value="${escapeHtml(p.subtitle || '')}"></label>
      <label>描述<textarea name="description">${escapeHtml(p.description || '')}</textarea></label>
      <label>类型
        <select name="productType" id="mp-type">
          <option value="physical" ${p.productType === 'physical' ? 'selected' : ''}>实物</option>
          <option value="virtual" ${p.productType === 'virtual' ? 'selected' : ''}>虚拟</option>
        </select>
      </label>
      <div id="mp-virtual-wrap" style="display:${p.productType === 'virtual' ? '' : 'none'}">
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
  const footer = `<button type="button" id="mp-cancel">取消</button>
    <button type="button" id="mp-save" class="mc-primary">保存</button>`;
  adminOpenModal(p.id ? '编辑商品' : '新增商品', body, footer);
  document.getElementById('mp-type').onchange = (e) => {
    document.getElementById('mp-virtual-wrap').style.display = (e.target.value === 'virtual') ? '' : 'none';
  };
  document.getElementById('mp-cancel').onclick = adminCloseModal;
  document.getElementById('mp-save').onclick = () => submitMcProduct();
}

async function submitMcProduct() {
  const form = document.getElementById('mp-form');
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
    await adminPost('/api/merchant/product/save', body);
    adminCloseModal();
    loadMcProducts();
  } catch (e) { adminError(e); }
}

async function toggleMcProduct(id, status) {
  try {
    await adminPost('/api/merchant/product/status', { id: Number(id), status: Number(status) });
    loadMcProducts();
  } catch (e) { adminError(e); }
}

async function deleteMcProduct(id) {
  if (!confirm('确定删除该商品？已产生订单的商品无法删除，只能下架。')) return;
  try {
    await adminPost('/api/merchant/product/delete', { id: Number(id) });
    loadMcProducts();
  } catch (e) { adminError(e); }
}

// ============ 订单 ============
async function renderMcOrders() {
  const el = document.getElementById('adminContent');
  el.innerHTML = `
    <div class="mc-toolbar">
      <input id="mo-kw" placeholder="订单号 / 收货人" value="${escapeHtml(MC.orderKeyword)}">
      <select id="mo-status">
        <option value="">全部状态</option>
        ${[0,1,2,3,4,5].map(s =>
      `<option value="${s}" ${MC.orderStatus===String(s)?'selected':''}>${orderStatusText(s)}</option>`
  ).join('')}
      </select>
      <button id="mo-search">搜索</button>
    </div>
    <div id="mo-list">加载中...</div>`;
  document.getElementById('mo-search').onclick = () => {
    MC.orderKeyword = document.getElementById('mo-kw').value.trim();
    MC.orderStatus = document.getElementById('mo-status').value;
    MC.orderPage = 1; loadMcOrders();
  };
  loadMcOrders();
}

async function loadMcOrders() {
  const listEl = document.getElementById('mo-list');
  listEl.innerHTML = '加载中...';
  try {
    const data = await adminPost('/api/merchant/order/list', {
      page: MC.orderPage, size: 10,
      keyword: MC.orderKeyword || null,
      status: MC.orderStatus !== '' ? Number(MC.orderStatus) : null
    });
    const records = data.records || [];
    const total = Number(data.total || 0);
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
        <td>${o.createTime ? escapeHtml(String(o.createTime).replace('T',' ')) : '-'}</td>
        <td>
          <button class="mo-detail" data-no="${escapeHtml(o.orderNo)}">详情</button>
          ${o.status === 1 ? `<button class="mo-ship" data-no="${escapeHtml(o.orderNo)}">发货</button>` : ''}
        </td></tr>`;
    });
    html += `</tbody></table>` + adminPager('mo', total, MC.orderPage, 10);
    listEl.innerHTML = html;

    listEl.querySelectorAll('.mo-detail').forEach(b => b.onclick = () => openMcOrderDetail(b.dataset.no));
    listEl.querySelectorAll('.mo-ship').forEach(b => b.onclick = () => openMcShipModal(b.dataset.no));
    const prev = listEl.querySelector('.mo-prev');
    const next = listEl.querySelector('.mo-next');
    if (prev) prev.onclick = () => { if (MC.orderPage > 1) { MC.orderPage--; loadMcOrders(); } };
    if (next) next.onclick = () => { MC.orderPage++; loadMcOrders(); };
  } catch (e) { adminError(e); listEl.innerHTML = '加载失败'; }
}

async function openMcOrderDetail(orderNo) {
  try {
    const data = await adminPost('/api/merchant/order/detail', { orderNo });
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
    adminOpenModal('订单详情', body, `<button type="button" id="mo-close">关闭</button>`);
    document.getElementById('mo-close').onclick = adminCloseModal;
  } catch (e) { adminError(e); }
}

function openMcShipModal(orderNo) {
  const body = `
    <form id="mo-ship-form" class="mc-form">
      <input type="hidden" name="orderNo" value="${escapeHtml(orderNo)}">
      <label>快递单号<input name="expressNo" required></label>
    </form>`;
  const footer = `<button type="button" id="mo-ship-cancel">取消</button>
    <button type="button" id="mo-ship-save" class="mc-primary">确认发货</button>`;
  adminOpenModal('订单发货', body, footer);
  document.getElementById('mo-ship-cancel').onclick = adminCloseModal;
  document.getElementById('mo-ship-save').onclick = async () => {
    const fd = new FormData(document.getElementById('mo-ship-form'));
    try {
      await adminPost('/api/merchant/order/ship', {
        orderNo: fd.get('orderNo'),
        expressNo: (fd.get('expressNo') || '').trim()
      });
      adminCloseModal();
      loadMcOrders();
    } catch (e) { adminError(e); }
  };
}

// ============ 退款 ============
async function renderMcRefunds() {
  const el = document.getElementById('adminContent');
  el.innerHTML = `
    <div class="mc-toolbar">
      <select id="mr-status">
        <option value="">全部状态</option>
        ${[1,2,3,4,5].map(s =>
      `<option value="${s}" ${MC.refundStatus===String(s)?'selected':''}>${refundStatusText(s)}</option>`
  ).join('')}
      </select>
      <button id="mr-search">搜索</button>
    </div>
    <div id="mr-list">加载中...</div>`;
  document.getElementById('mr-search').onclick = () => {
    MC.refundStatus = document.getElementById('mr-status').value;
    MC.refundPage = 1; loadMcRefunds();
  };
  loadMcRefunds();
}

async function loadMcRefunds() {
  const listEl = document.getElementById('mr-list');
  listEl.innerHTML = '加载中...';
  try {
    const data = await adminPost('/api/merchant/refund/list', {
      page: MC.refundPage, size: 10,
      status: MC.refundStatus !== '' ? Number(MC.refundStatus) : null
    });
    const list = data.records || [];
    const total = Number(data.total || 0);
    let html = `<table class="mc-table"><thead><tr>
      <th>退款单号</th><th>订单号</th><th>退款金额</th><th>原因</th><th>状态</th><th>申请时间</th><th>操作</th>
    </tr></thead><tbody>`;
    if (!list.length) html += `<tr><td colspan="7">暂无退款单</td></tr>`;
    list.forEach(r => {
      html += `<tr>
        <td>${escapeHtml(r.refundNo)}</td>
        <td>${escapeHtml(r.orderNo)}</td>
        <td>¥${r.refundAmount ?? 0}</td>
        <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(r.reason || '-')}</td>
        <td>${refundStatusText(r.status)}</td>
        <td>${r.createTime ? escapeHtml(String(r.createTime).replace('T',' ')) : '-'}</td>
        <td>${r.status === 1 ? `<button class="mr-review" data-id="${r.id}">审核</button>` : '-'}</td>
      </tr>`;
    });
    html += `</tbody></table>` + adminPager('mr', total, MC.refundPage, 10);
    listEl.innerHTML = html;

    listEl.querySelectorAll('.mr-review').forEach(b => b.onclick = () => openMcRefundModal(b.dataset.id));
    const prev = listEl.querySelector('.mr-prev');
    const next = listEl.querySelector('.mr-next');
    if (prev) prev.onclick = () => { if (MC.refundPage > 1) { MC.refundPage--; loadMcRefunds(); } };
    if (next) next.onclick = () => { MC.refundPage++; loadMcRefunds(); };
  } catch (e) { adminError(e); listEl.innerHTML = '加载失败'; }
}

function openMcRefundModal(refundId) {
  const body = `
    <form id="mr-form" class="mc-form">
      <input type="hidden" name="refundId" value="${refundId}">
      <label>处理结果
        <select name="approve" id="mr-approve">
          <option value="true">同意退款</option>
          <option value="false">拒绝退款</option>
        </select>
      </label>
      <div id="mr-reject-wrap" style="display:none">
        <label>拒绝理由<input name="rejectReason"></label>
      </div>
    </form>`;
  const footer = `<button type="button" id="mr-cancel">取消</button>
    <button type="button" id="mr-save" class="mc-primary">提交</button>`;
  adminOpenModal('退款审核', body, footer);
  document.getElementById('mr-approve').onchange = (e) => {
    document.getElementById('mr-reject-wrap').style.display = (e.target.value === 'false') ? '' : 'none';
  };
  document.getElementById('mr-cancel').onclick = adminCloseModal;
  document.getElementById('mr-save').onclick = async () => {
    const fd = new FormData(document.getElementById('mr-form'));
    const approve = fd.get('approve') === 'true';
    const rejectReason = (fd.get('rejectReason') || '').trim();
    if (!approve && !rejectReason) { alert('拒绝退款时必须填写理由'); return; }
    try {
      await adminPost('/api/merchant/refund/review', {
        refundId: Number(fd.get('refundId')),
        approve,
        rejectReason: approve ? null : rejectReason
      });
      adminCloseModal();
      loadMcRefunds();
    } catch (e) { adminError(e); }
  };
}

// ============ 店铺设置 ============
async function renderMcShop() {
  const el = document.getElementById('adminContent');
  el.innerHTML = '加载中...';
  try {
    const m = await adminPost('/api/merchant/shop/info');
    el.innerHTML = `
      <div class="mc-form" style="max-width:520px;">
        <label>店铺名称<input id="ms-shopName" value="${escapeHtml(m.shopName || '')}"></label>
        <label>Logo<input id="ms-shopLogo" value="${escapeHtml(m.logo || '')}"></label>
        <label>简介<textarea id="ms-shopDesc">${escapeHtml(m.description || '')}</textarea></label>
        <label>联系电话<input id="ms-shopPhone" value="${escapeHtml(m.contactPhone || '')}"></label>
        <div class="mc-form-actions">
          <button id="ms-shop-save" class="mc-primary">保存</button>
        </div>
      </div>`;
    document.getElementById('ms-shop-save').onclick = saveMcShop;
  } catch (e) { adminError(e); el.innerHTML = '加载失败'; }
}

async function saveMcShop() {
  const body = {
    shopName: document.getElementById('ms-shopName').value.trim(),
    logo: document.getElementById('ms-shopLogo').value.trim() || null,
    description: document.getElementById('ms-shopDesc').value.trim() || null,
    contactPhone: document.getElementById('ms-shopPhone').value.trim() || null
  };
  if (!body.shopName) { alert('店铺名称不能为空'); return; }
  try {
    await adminPost('/api/merchant/shop/update', body);
    alert('店铺信息已更新');
    renderMcShop();
  } catch (e) { adminError(e); }
}