// js/pages/order-detail.js
// 订单详情页

async function loadOrderDetail(){
    const wrap = document.getElementById('orderDetailContent');
    if(!wrap) return;

    const orderNo = localStorage.getItem('viewOrderNo');
    if(!orderNo){
        wrap.innerHTML = '<div class="empty-tip">订单号不存在</div>';
        return;
    }

    try{
        const json = await request(`/api/order/detail?orderNo=${orderNo}`);
        if(!json.success){
            wrap.innerHTML= `<div class="empty-tip">${json.msg || '加载失败'}</div>`;
            return ;
        }

        const {order,items, canCancel, merchant} = json.data;
        const statusMap = {
            0: { text: '待付款', color: '#fb923c' },
            1: { text: '已付款', color: '#22c55e' },
            2: { text: '已发货', color: '#3b82f6' },
            3: { text: '已完成', color: '#94a3b8' },
            4: { text: '已取消', color: '#64748b' },
            5: { text: '已退款', color: '#ef4444' }
        };
        const status = statusMap[order.status]||{text: '未知', color: '#999'};

    //     渲染详情
        let html = `
            <div class="detail-section">
                <div class="detail-status" style="color:${status.color};">${status.text}</div>
                <div class="detail-row">
                    <span class="detail-label">订单号：</span>
                    <span>${order.orderNo}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">下单时间：</span>
                    <span>${(order.createTime || '').replace('T', ' ')}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">实付金额：</span>
                    <span class="detail-amount">¥${Number(order.payAmount || 0).toFixed(2)}</span>
                </div>
        `;

        if(order.receiver){
            html += `
                <div class="detail-row">
                    <span class="detail-label">收件人：</span>
                    <span>${order.receiver} ${order.phone || ''}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">收货地址：</span>
                    <span>${order.addressDetail || ''}</span>
                </div>
            `;
        }
        html += `</div>`;

    //     商品明细
        html += `<div class="detail-section">
            <div class="detail-goods-title">商品明细</div>`;
        items.forEach(item => {
            const reviewHtml = order.status === 3
                ? (item.reviewed
                    ? `<div class="order-reviewed">已评价 <span class="order-stars">${'★'.repeat(item.reviewRating || 5)}</span></div>`
                    : `<button class="order-review-btn" data-item-id="${item.id}" data-name="${escapeHtml(item.productName)}">评价</button>`)
                : '';
            html += `
                <div class="order-goods-item">
                    <img class="order-goods-cover" src="${item.productCover || ''}" alt="${item.productName}">
                    <div class="order-goods-info">
                        <div class="order-goods-name">${escapeHtml(item.productName)}</div>
                        <div class="order-goods-meta">
                            <span class="order-goods-price">¥${item.price}</span>
                            <span class="order-goods-qty">×${item.quantity}</span>
                        </div>
                    </div>
                    <div class="order-goods-right">
                        <div class="order-goods-subtotal">¥${Number(item.subtotal).toFixed(2)}</div>
                        ${reviewHtml}
                    </div>
                </div>
            `;
        });
        html += `</div>`;

        // 商家信息
        if (merchant) {
            html += `
                <div class="detail-section order-merchant">
                    ${merchant.logo
                        ? `<img class="order-merchant-logo" src="${merchant.logo}" alt="">`
                        : `<div class="order-merchant-logo order-merchant-logo-text">${escapeHtml((merchant.shopName || '店').charAt(0))}</div>`}
                    <div class="order-merchant-info">
                        <div class="order-merchant-name">${escapeHtml(merchant.shopName || '')}</div>
                        <div class="order-merchant-desc">${escapeHtml(merchant.description || '')}</div>
                    </div>
                    <button class="order-contact-btn" id="contactMerchantBtn">联系商家</button>
                </div>
            `;
        }

        const cancellable = (typeof order.canCancel === 'boolean')
            ? order.canCancel
            : canCancelDetail(order, items);

        let actions = '';
        if (order.status === 2) {
            actions += `<button class="order-receive-btn" id="detailReceiveBtn">确认收货</button>`;
        }
        if (cancellable) {
            actions += `
                <button class="order-cancel-btn" id="detailCancelBtn" data-order-no="${order.orderNo}">
                    取消订单
                </button>
            `;
        }
        if (actions) {
            html += `<div class="detail-actions">${actions}</div>`;
        }

        wrap.innerHTML = html;
        // 绑定取消事件
        const cancelBtn = document.getElementById('detailCancelBtn');
        if(cancelBtn){
            cancelBtn.onclick = async ()=>{
                if(!confirm('确定取消该订单吗？')) return;
                cancelBtn.disabled = true;
                cancelBtn.innerText = '取消中...';
                try{
                    const res =await  request(`/api/order/cancel?orderNo=${orderNo}`, { method:'POST' });
                    if(res.success){
                        alert('订单已取消');
                        await loadOrderDetail();  // 刷新详情
                    }else{
                        alert(res.msg || '取消失败');
                        cancelBtn.disabled = false;
                        cancelBtn.innerText = '取消订单';
                    }
                }catch (e) {
                    console.error('[order-detail] 取消失败：', e);
                    alert('网络异常');
                    cancelBtn.disabled = false;
                    cancelBtn.innerText = '取消订单';
                }
            };
        }

        // 确认收货
        const receiveBtn = document.getElementById('detailReceiveBtn');
        if (receiveBtn) {
            receiveBtn.onclick = async () => {
                if (!confirm('确认已经收到商品了吗？')) return;
                receiveBtn.disabled = true;
                receiveBtn.innerText = '处理中...';
                try {
                    await request(`/api/order/receive?orderNo=${orderNo}`, { method: 'POST' });
                    alert('已确认收货，可以为商品评价了');
                    await loadOrderDetail();
                } catch (e) {
                    alert(e.message || '操作失败');
                    receiveBtn.disabled = false;
                    receiveBtn.innerText = '确认收货';
                }
            };
        }

        // 联系商家
        const contactBtn = document.getElementById('contactMerchantBtn');
        if (contactBtn && merchant) {
            contactBtn.onclick = async () => {
                try {
                    const r = await request(`/api/chat/open-merchant?merchantId=${merchant.id}`, { method: 'POST' });
                    location.hash = '#/chat?id=' + r.data.id;
                } catch (e) {
                    alert(e.message || '打开会话失败');
                }
            };
        }

        // 逐件评价
        wrap.querySelectorAll('.order-review-btn').forEach(btn => {
            btn.onclick = () => openReviewModal(btn.dataset.itemId, btn.dataset.name);
        });

    }catch (e) {
        console.error('[order-detail] 加载失败：', e);
        wrap.innerHTML = '<div class="empty-tip">网络异常</div>';
    }
}

function canCancelDetail(order,items){
    if(!order) return false;
    const st = order.status;
    if( st ===0 )return true;
    if( st ===1 || st===2 ){
        return (items || []).some(i =>i.productType === 'physical');
    }
    return false;
}


// 根据 refundStatus 展示不同 UI
function renderRefundBlock(order, refunds) {
    if (!order.refundStatus || order.refundStatus === 0) {
        return `<button onclick="applyRefund('${order.orderNo}')">申请退款</button>`;
    }
    const map = {1:'退款申请中', 2:'退款处理中', 3:'退款成功', 4:'退款失败', 5:'退款被拒绝'};
    let html = `<div class="refund-status">${map[order.refundStatus] || '处理中'}</div>`;
    if (order.refundStatus === 4 || order.refundStatus === 5) {
        html += `<button onclick="applyRefund('${order.orderNo}')">重新申请</button>`;
    }
    return html;
}

async function applyRefund(orderNo) {
    const reason = prompt("请输入退款原因（可留空）：") || "";
    if (!confirm("确认申请退款？审核通过后退款将原路返回。")) return;
    const res = await request.post(`/api/refund/apply?orderNo=${orderNo}&reason=${encodeURIComponent(reason)}`);
    alert(res.success ? res.msg : (res.msg || "申请失败"));
    if (res.success) location.reload();
}
window.applyRefund = applyRefund;

// ================= 评价弹窗 =================
let reviewItemId = null;
let reviewRating = 5;
let reviewImages = [];

function ensureReviewModal() {
    if (document.getElementById('reviewModal')) return;
    const modal = document.createElement('div');
    modal.id = 'reviewModal';
    modal.className = 'review-modal';
    modal.innerHTML = `
        <div class="review-modal-box">
            <div class="review-modal-head">
                <span>评价商品</span>
                <button type="button" class="review-modal-close" id="reviewCloseBtn">×</button>
            </div>
            <div class="review-product-name" id="reviewProductName"></div>
            <div class="review-stars" id="reviewStars">
                <span data-star="1">★</span><span data-star="2">★</span><span data-star="3">★</span>
                <span data-star="4">★</span><span data-star="5">★</span>
            </div>
            <textarea id="reviewContent" maxlength="500" placeholder="说说这件商品怎么样，至少写点感受..."></textarea>
            <div class="review-image-preview" id="reviewImagePreview"></div>
            <div class="review-image-bar">
                <button type="button" class="review-image-btn" id="reviewImageBtn">添加图片（最多3张）</button>
                <input type="file" id="reviewImageInput" accept="image/*" multiple style="display:none;">
                <label class="review-anonymous"><input type="checkbox" id="reviewAnonymous"> 匿名评价</label>
            </div>
            <div class="review-modal-actions">
                <button type="button" class="review-btn-cancel" id="reviewCancelBtn">取消</button>
                <button type="button" class="review-btn-submit" id="reviewSubmitBtn">提交评价</button>
            </div>
        </div>
    `;
    document.body.appendChild(modal);

    // 星级
    modal.querySelector('#reviewStars').addEventListener('click', e => {
        const star = e.target.closest('[data-star]');
        if (!star) return;
        reviewRating = Number(star.dataset.star);
        renderReviewStars();
    });

    // 图片选择与上传
    const fileInput = modal.querySelector('#reviewImageInput');
    modal.querySelector('#reviewImageBtn').onclick = () => fileInput.click();
    fileInput.onchange = async () => {
        const files = Array.from(fileInput.files || []);
        fileInput.value = '';
        for (const file of files) {
            if (reviewImages.length >= 3) break;
            if (!file.type || !file.type.startsWith('image/')) continue;
            const form = new FormData();
            form.append('file', file);
            try {
                const json = await request('/api/review/upload', { method: 'POST', body: form });
                reviewImages.push(json.data);
                renderReviewImages();
            } catch (e) {
                alert(e.message || '图片上传失败');
            }
        }
    };

    modal.querySelector('#reviewImagePreview').addEventListener('click', e => {
        const btn = e.target.closest('[data-remove]');
        if (!btn) return;
        reviewImages.splice(Number(btn.dataset.remove), 1);
        renderReviewImages();
    });

    const close = () => modal.classList.remove('show');
    modal.querySelector('#reviewCloseBtn').onclick = close;
    modal.querySelector('#reviewCancelBtn').onclick = close;
    modal.addEventListener('click', e => {
        if (e.target === modal) close();
    });

    modal.querySelector('#reviewSubmitBtn').onclick = async () => {
        const btn = modal.querySelector('#reviewSubmitBtn');
        const content = (modal.querySelector('#reviewContent').value || '').trim();
        if (!content) {
            alert('请填写评价内容');
            return;
        }
        btn.disabled = true;
        try {
            await request('/api/review', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    orderItemId: Number(reviewItemId),
                    rating: reviewRating,
                    content,
                    images: reviewImages,
                    anonymous: modal.querySelector('#reviewAnonymous').checked
                })
            });
            close();
            alert('评价成功');
            await loadOrderDetail();
        } catch (e) {
            alert(e.message || '评价失败');
            btn.disabled = false;
        }
    };
}

function renderReviewStars() {
    document.querySelectorAll('#reviewStars span').forEach(el => {
        el.classList.toggle('active', Number(el.dataset.star) <= reviewRating);
    });
}

function renderReviewImages() {
    const box = document.getElementById('reviewImagePreview');
    if (!box) return;
    box.innerHTML = reviewImages.map((url, i) => `
        <div class="review-image-item">
            <img src="${url}" alt="">
            <button type="button" data-remove="${i}">×</button>
        </div>
    `).join('');
}

function openReviewModal(itemId, productName) {
    ensureReviewModal();
    reviewItemId = itemId;
    reviewRating = 5;
    reviewImages = [];
    document.getElementById('reviewProductName').textContent = productName || '';
    document.getElementById('reviewContent').value = '';
    document.getElementById('reviewAnonymous').checked = false;
    document.getElementById('reviewSubmitBtn').disabled = false;
    renderReviewStars();
    renderReviewImages();
    document.getElementById('reviewModal').classList.add('show');
}

// 注册
registerPageHook('order-detail', async () => {
    await loadOrderDetail();
});
