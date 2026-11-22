// js/admin/admin-common.js
// 后台公共：权限上下文、请求封装、菜单定义、弹窗、分页

const AdminCtx = {
    role: 0,          // 0用户 1商家 2管理员
    userId: null,
    username: '',
    current: null     // 当前菜单 key
};

// 状态字典
const ADMIN_ORDER_STATUS = { 0:'待付款',1:'已付款',2:'已发货',3:'已完成',4:'已取消',5:'已退款' };
const ADMIN_REFUND_STATUS = { 1:'申请中',2:'退款中',3:'退款成功',4:'退款失败',5:'已拒绝' };
const ADMIN_ROLE_TEXT = { 0:'普通用户',1:'商家',2:'管理员' };
const orderStatusText  = s => ADMIN_ORDER_STATUS[s] || '未知';
const refundStatusText = s => ADMIN_REFUND_STATUS[s] || '未知';
const roleText         = r => ADMIN_ROLE_TEXT[r] || '未知';

// 统一 POST(JSON)
async function adminPost(url, body) {
    const json = await request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {})
    });
    if (!json.success) throw new Error(json.msg || '请求失败');
    return json.data;
}
// 统一 POST(form / query 参数)
async function adminPostForm(url, params) {
    const qs = new URLSearchParams(params || {}).toString();
    const json = await request(url + (qs ? ('?' + qs) : ''), { method: 'POST' });
    if (!json.success) throw new Error(json.msg || '请求失败');
    return json.data;
}
function adminError(e) { console.error(e); alert(e.message || '操作失败'); }

// ============ 弹窗 ============
function adminOpenModal(title, bodyHtml, footerHtml) {
    document.getElementById('adminModalTitle').innerHTML = title;
    document.getElementById('adminModalBody').innerHTML = bodyHtml;
    document.getElementById('adminModalFooter').innerHTML = footerHtml || '';
    document.getElementById('adminModal').style.display = 'flex';
}
function adminCloseModal() {
    document.getElementById('adminModal').style.display = 'none';
}

// ============ 通用分页 ============
function adminPager(prefix, total, page, size) {
    const totalPages = Math.max(1, Math.ceil(total / size));
    return `
      <div class=\"mc-pagination\">
        <button class=\"${prefix}-prev\" ${page <= 1 ? 'disabled' : ''}>上一页</button>
        <span>第 ${page} / ${totalPages} 页（共 ${total} 条）</span>
        <button class=\"${prefix}-next\" ${page >= totalPages ? 'disabled' : ''}>下一页</button>
      </div>`;
}

// ============ 菜单定义 ============
// 根据角色过滤：管理员(2) 看全部；商家(1) 只看商家菜单
const ADMIN_MENUS = [
    { group: '平台管理', minRole: 2 },
    { key: 'dashboard',    title: '数据看板',  minRole: 2, render: () => renderAdminDashboard() },
    { key: 'users',        title: '用户管理',  minRole: 2, render: () => renderAdminUsers() },
    { key: 'shops',        title: '店铺管理',  minRole: 2, render: () => renderAdminShops() },
    { key: 'refunds',      title: '退款管理',  minRole: 2, render: () => renderAdminRefunds() },
    { key: 'vouchers',     title: '优惠券管理', minRole: 2, render: () => renderAdminVouchers() },

    { group: '商家管理', minRole: 1 },
    { key: 'mc-dashboard', title: '商家看板',  minRole: 1, render: () => renderMcDashboard() },
    { key: 'mc-products',  title: '我的商品',  minRole: 1, render: () => renderMcProducts() },
    { key: 'mc-orders',    title: '我的订单',  minRole: 1, render: () => renderMcOrders() },
    { key: 'mc-refunds',   title: '退款处理',  minRole: 1, render: () => renderMcRefunds() },
    { key: 'mc-shop',      title: '店铺设置',  minRole: 1, render: () => renderMcShop() }
];