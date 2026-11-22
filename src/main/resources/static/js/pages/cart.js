// js/pages/cart.js
// 购物车页面

// ========== 状态 ==========
const cartState = {
    list: [],           // 购物车列表
    allSelected: false  // 全选状态
};

// ========== 加载购物车 ==========
async function loadCartList() {
    const wrap = document.getElementById('cartList');
    const footer = document.getElementById('cartFooter');
    if (!wrap) return;

    wrap.innerHTML = '<div class="empty-tip">加载中...</div>';
    if (footer) footer.style.display = 'none';

    try {
        const json = await request('/api/cart/list');

        // 未登录
        if (!json.success) {
            wrap.innerHTML = `
                <div class="cart-empty">
                    ${json.msg || '加载失败'}
                    <a href="javascript:void(0)" onclick="location.hash='#/login'">去登录</a>
                </div>
            `;
            return;
        }

        const list = json.data || [];
        cartState.list = list;

        if (list.length === 0) {
            wrap.innerHTML = `
                <div class="cart-empty">
                    购物车还是空的，
                    <a href="javascript:void(0)" onclick="location.hash='#/shop'">去逛逛</a>
                </div>
            `;
            return;
        }

        // 渲染列表
        renderCartList(list);
        if (footer) footer.style.display = 'flex';

        // 更新总价
        updateCartTotal();

    } catch (err) {
        console.error('[cart] 加载失败：', err);
        wrap.innerHTML = '<div class="empty-tip">网络异常</div>';
    }
}

// ========== 渲染购物车列表 ==========
function renderCartList(list) {
    const wrap = document.getElementById('cartList');
    wrap.innerHTML = '';

    list.forEach(item => {
        const div = document.createElement('div');
        div.className = 'cart-item';
        div.dataset.cartId = item.id;

        // 勾选框
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'cart-item-checkbox';
        checkbox.checked = item.selected === 1;
        checkbox.onchange = () => {
            item.selected = checkbox.checked ? 1 : 0;
            updateCartTotal();
            updateSelectAllState();
        };
        div.appendChild(checkbox);

        // 封面
        const cover = document.createElement('img');
        cover.className = 'cart-item-cover';
        cover.src = item.coverUrl || '';
        cover.alt = item.name;
        div.appendChild(cover);

        // 信息
        const info = document.createElement('div');
        info.className = 'cart-item-info';
        info.innerHTML = `
            <div class="cart-item-name">${escapeHtml(item.name)}</div>
            <div class="cart-item-subtitle">${escapeHtml(item.subtitle || '')}</div>
            <div class="cart-item-price">${item.price}</div>
        `;
        div.appendChild(info);

        // 数量
        const qty = document.createElement('div');
        qty.className = 'cart-item-qty';
        qty.innerHTML = `
            <button class="qty-minus" ${item.quantity <= 1 ? 'disabled' : ''}>−</button>
            <span class="qty-num">${item.quantity}</span>
            <button class="qty-plus" ${item.stock != null && item.quantity >= item.stock ? 'disabled' : ''}>+</button>
        `;
        div.appendChild(qty);

        // 删除按钮
        const removeBtn = document.createElement('button');
        removeBtn.className = 'del-btn';
        removeBtn.title = '删除';
        removeBtn.innerHTML = DEL_ICON;
        div.appendChild(removeBtn);

        // ===== 绑定事件 =====
        // 减数量
        qty.querySelector('.qty-minus').onclick = () => {
            if (item.quantity > 1) {
                updateCartQuantity(item.id, item.quantity - 1);
            }
        };
        // 加数量
        qty.querySelector('.qty-plus').onclick = () => {
            if (item.stock == null || item.quantity < item.stock) {
                updateCartQuantity(item.id, item.quantity + 1);
            } else {
                alert('已达库存上限');
            }
        };
        // 删除
        removeBtn.onclick = () => {
            if (confirm(`确定从购物车删除「${item.name}」吗？`)) {
                removeCartItem(item.id);
            }
        };

        wrap.appendChild(div);
    });
}

// ========== 修改数量 ==========
async function updateCartQuantity(cartId, newQuantity) {
    try {
        const json = await request(`/api/cart/update?cartId=${cartId}&quantity=${newQuantity}`, {
            method: 'POST'
        });
        if (json.success) {
            // 重新加载
            await loadCartList();
            // 更新顶部角标
            refreshCartBadge();
        } else {
            alert(json.msg || '修改失败');
        }
    } catch (err) {
        console.error('[cart] 修改数量失败：', err);
        alert('网络异常');
    }
}

// ========== 删除购物车项 ==========
async function removeCartItem(cartId) {
    try {
        const json = await request(`/api/cart/remove?cartId=${cartId}`, {
            method: 'POST'
        });
        if (json.success) {
            await loadCartList();
            refreshCartBadge();
        } else {
            alert(json.msg || '删除失败');
        }
    } catch (err) {
        console.error('[cart] 删除失败：', err);
        alert('网络异常');
    }
}

// ========== 计算总价 ==========
function updateCartTotal() {
    let total = 0;
    let count = 0;
    cartState.list.forEach(item => {
        if (item.selected === 1) {
            total += Number(item.price) * item.quantity;
            count += item.quantity;
        }
    });
    const totalEl = document.getElementById('cartTotalPrice');
    if (totalEl) {
        totalEl.innerText = '¥' + total.toFixed(2);
    }
    // 结算按钮是否可点
    const checkoutBtn = document.getElementById('cartCheckoutBtn');
    if (checkoutBtn) {
        checkoutBtn.disabled = count === 0;
    }
    // 全选状态
    updateSelectAllState();
}

// ========== 全选状态同步 ==========
function updateSelectAllState() {
    const selectAll = document.getElementById('cartSelectAll');
    if (!selectAll) return;
    const allChecked = cartState.list.length > 0 && cartState.list.every(item => item.selected === 1);
    selectAll.checked = allChecked;
}

// ========== 刷新顶部角标 ==========
async function refreshCartBadge() {
    const badge = document.getElementById('cartBadge');
    if (!badge) return;

    try {
        const json = await request('/api/cart/count');
        const count = json.success ? (json.data || 0) : 0;
        if (count > 0) {
            badge.innerText = count > 99 ? '99+' : count;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    } catch (err) {
        console.warn('[cart] 刷新角标失败：', err);
        badge.style.display = 'none';
    }
}

// ========== 绑定事件（只绑一次） ==========
let cartEventsBound = false;
function bindCartEvents() {
    if (cartEventsBound) return;
    cartEventsBound = true;

    // 全选/全不选
    const selectAll = document.getElementById('cartSelectAll');
    if (selectAll) {
        selectAll.onchange = () => {
            const checked = selectAll.checked;
            cartState.list.forEach(item => {
                item.selected = checked ? 1 : 0;
            });
            // 重新渲染
            renderCartList(cartState.list);
            updateCartTotal();
        };
    }

    // 去结算
    const checkoutBtn = document.getElementById('cartCheckoutBtn');
    if (checkoutBtn) {
        checkoutBtn.onclick = () => {
            const selected = cartState.list.filter(item => item.selected === 1);
            if (selected.length === 0) {
                alert('请先选择要结算的商品');
                return;
            }
            localStorage.setItem('checkoutData',JSON.stringify({
                source:'cart',
                items:selected.map(item=>({
                    productId: item.productId,
                    quantity: item.quantity
                }))
            }));
            location.hash='#/order-confirm';
        };
    }

    // 删除选中
    const removeSelectedBtn = document.getElementById('cartRemoveSelected');
    if (removeSelectedBtn) {
        removeSelectedBtn.onclick = async () => {
            const selected = cartState.list.filter(item => item.selected === 1);
            if (selected.length === 0) {
                alert('请先选择要删除的商品');
                return;
            }
            if (!confirm(`确定删除选中的 ${selected.length} 件商品吗？`)) return;

            try {
                for (const item of selected) {
                    await request(`/api/cart/remove?cartId=${item.id}`, { method: 'POST' });
                }
                await loadCartList();
                refreshCartBadge();
            } catch (err) {
                console.error('[cart] 批量删除失败：', err);
                alert('网络异常');
            }
        };
    }
}

// ========== 注册到路由 ==========
registerPageHook('cart', async () => {
    bindCartEvents();
    await loadCartList();
});