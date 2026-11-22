// js/pages/address.js
// 收货地址管理

// ========== 状态 ==========
const addressState = {
    list: [],
    editingId: null   // 正在编辑的地址 id，null 表示新增
};

// ========== 加载地址列表 ==========
async function loadAddressList() {
    const wrap = document.getElementById('addressList');
    if (!wrap) return;
    wrap.innerHTML = '<div class="empty-tip">加载中...</div>';

    try {
        const json = await request('/api/address/list');
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
        addressState.list = list;

        if (list.length === 0) {
            wrap.innerHTML = '<div class="empty-tip">还没有收货地址，点右上角新增</div>';
            return;
        }

        renderMyAddressList(list);
    } catch (err) {
        console.error('[address] 加载失败：', err);
        wrap.innerHTML = '<div class="empty-tip">网络异常</div>';
    }
}

// ========== 渲染地址列表 ==========
function renderMyAddressList(list) {
    const wrap = document.getElementById('addressList');
    wrap.innerHTML = '';

    list.forEach(addr => {
        const div = document.createElement('div');
        div.className = 'address-item';

        const isDefault = addr.isDefault === 1;

        div.innerHTML = `
            <div class="address-main">
                <div class="address-header">
                    <span class="address-receiver">${escapeHtml(addr.receiver)}</span>
                    <span class="address-phone">${escapeHtml(addr.phone)}</span>
                    ${isDefault ? '<span class="address-default-tag">默认</span>' : ''}
                </div>
                <div class="address-detail">
                    ${escapeHtml(addr.province || '')} ${escapeHtml(addr.city || '')} ${escapeHtml(addr.district || '')} ${escapeHtml(addr.detail)}
                </div>
            </div>
            <div class="address-actions">
                ${!isDefault ? `<button class="addr-btn addr-default-btn" data-id="${addr.id}">设为默认</button>` : ''}
                <button class="addr-btn addr-edit-btn" data-id="${addr.id}">编辑</button>
                <button class="del-btn addr-delete-btn" data-id="${addr.id}" title="删除">${DEL_ICON}</button>
            </div>
        `;
        wrap.appendChild(div);
    });

    // 绑定事件（事件委托）
    wrap.querySelectorAll('.addr-default-btn').forEach(btn => {
        btn.onclick = () => setDefaultAddress(Number(btn.dataset.id));
    });
    wrap.querySelectorAll('.addr-edit-btn').forEach(btn => {
        btn.onclick = () => openAddressModal(Number(btn.dataset.id));
    });
    wrap.querySelectorAll('.addr-delete-btn').forEach(btn => {
        btn.onclick = () => deleteAddress(Number(btn.dataset.id));
    });
}

// ========== 打开弹窗（新增或编辑） ==========
function openAddressModal(editId) {

    const saveBtn = document.getElementById('addressModalSave');
    if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.innerText = '保存';
    }
    addressState.editingId = editId || null;

    const modal = document.getElementById('addressModal');
    const title = document.getElementById('addressModalTitle');
    const modalTitle = title;
    // 重置表单
    document.getElementById('addrReceiver').value = '';
    document.getElementById('addrPhone').value = '';
    document.getElementById('addrProvince').value = '';
    document.getElementById('addrCity').value = '';
    document.getElementById('addrDistrict').value = '';
    document.getElementById('addrDetail').value = '';
    document.getElementById('addrIsDefault').checked = false;

    if (editId) {
        // 编辑模式：填充表单
        title.innerText = '编辑地址';
        const addr = addressState.list.find(a => a.id === editId);
        if (addr) {
            document.getElementById('addrReceiver').value = addr.receiver || '';
            document.getElementById('addrPhone').value = addr.phone || '';
            document.getElementById('addrProvince').value = addr.province || '';
            document.getElementById('addrCity').value = addr.city || '';
            document.getElementById('addrDistrict').value = addr.district || '';
            document.getElementById('addrDetail').value = addr.detail || '';
            document.getElementById('addrIsDefault').checked = addr.isDefault === 1;
        }
    } else {
        title.innerText = '新增地址';
    }

    modal.style.display = 'flex';
}

// ========== 关闭弹窗 ==========
function closeAddressModal() {
    document.getElementById('addressModal').style.display = 'none';
    addressState.editingId = null;
}

// ========== 保存地址（提交表单） ==========
async function saveAddress() {
    const data = {
        receiver: document.getElementById('addrReceiver').value.trim(),
        phone: document.getElementById('addrPhone').value.trim(),
        province: document.getElementById('addrProvince').value.trim(),
        city: document.getElementById('addrCity').value.trim(),
        district: document.getElementById('addrDistrict').value.trim(),
        detail: document.getElementById('addrDetail').value.trim(),
        isDefault: document.getElementById('addrIsDefault').checked ? 1 : 0
    };

    // 前端校验
    if (!data.receiver) return alert('请填写收件人');
    if (!data.phone) return alert('请填写手机号');
    if (!/^1[3-9]\d{9}$/.test(data.phone)) return alert('手机号格式不正确');
    if (!data.detail) return alert('请填写详细地址');

    try {
        let url = '/api/address/add';
        if (addressState.editingId) {
            url = '/api/address/update';
            data.id = addressState.editingId;
        }

        const json = await request(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (json.success) {
            closeAddressModal();
            await loadAddressList();
        } else {
            alert(json.msg || '保存失败');
        }
    } catch (err) {
        console.error('[address] 保存失败：', err);
        alert('网络异常');
    }
}

// ========== 删除地址 ==========
async function deleteAddress(id) {
    if (!confirm('确定删除该地址吗？')) return;

    try {
        const json = await request(`/api/address/delete?addressId=${id}`, { method: 'POST' });
        if (json.success) {
            await loadAddressList();
        } else {
            alert(json.msg || '删除失败');
        }
    } catch (err) {
        console.error('[address] 删除失败：', err);
        alert('网络异常');
    }
}

// ========== 设为默认 ==========
async function setDefaultAddress(id) {
    try {
        const json = await request(`/api/address/setDefault?addressId=${id}`, { method: 'POST' });
        if (json.success) {
            await loadAddressList();
        } else {
            alert(json.msg || '设置失败');
        }
    } catch (err) {
        console.error('[address] 设置默认失败：', err);
        alert('网络异常');
    }
}

// ========== 绑定事件（只绑一次） ==========
let addressEventsBound = false;
function bindAddressEvents() {
    if (addressEventsBound) return;
    addressEventsBound = true;

    // 新增按钮
    const addBtn = document.getElementById('addressAddBtn');
    if (addBtn) addBtn.onclick = () => openAddressModal(null);

    // 弹窗关闭按钮
    const closeBtn = document.getElementById('addressModalClose');
    if (closeBtn) closeBtn.onclick = closeAddressModal;

    // 弹窗取消按钮
    const cancelBtn = document.getElementById('addressModalCancel');
    if (cancelBtn) cancelBtn.onclick = closeAddressModal;

    // 弹窗保存按钮
    const saveBtn = document.getElementById('addressModalSave');
    if (saveBtn) saveBtn.onclick = saveAddress;

    // 点击遮罩关闭弹窗
    const modal = document.getElementById('addressModal');
    if (modal) {
        modal.onclick = (e) => {
            if (e.target === modal) closeAddressModal();
        };
    }
}

// ========== 注册到路由 ==========
registerPageHook('address', async () => {
    bindAddressEvents();
    await loadAddressList();
});