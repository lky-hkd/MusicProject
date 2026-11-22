// js/pages/order-pay.js
// 支付页

// 支付批次号：sessionStorage 是每个标签页独立的一份（window.open 时会复制），
// 原支付页轮询成功后清理 localStorage，不会影响支付宝返回标签页里的副本。
function getPayNo() {
    return sessionStorage.getItem('payNo') || localStorage.getItem('payNo') || '';
}
function rememberPayNo(payNo) {
    if (!payNo) return;
    sessionStorage.setItem('payNo', payNo);
    localStorage.setItem('payNo', payNo);
}
function clearPayNo() {
    sessionStorage.removeItem('payNo');
    localStorage.removeItem('payNo');
}

//加载支付信息
async function loadPayPage(){
    const payNo = getPayNo();
    // ✅ 核心修复：每次进入支付页，强制重置按钮的初始状态！
    const btn = document.getElementById('payMockBtn');
    if (btn) {
        btn.disabled = false;
        btn.innerText = '模拟支付（点我直接成功）';
    }

    if(!payNo){
        // 订单可能已经支付成功，直接回订单列表查看，不再误报“订单丢失”
        location.hash = '#/my-orders';
        return;
    }
    rememberPayNo(payNo);

    document.getElementById('payOrderNo').innerText=payNo;
    //查订单详情获取金额
    try{
        const json = await request(`/api/order/pay-info?payNo=${payNo}`);
        if(json.success && json.data){
            document.getElementById('payAmount').innerText = '¥' + Number(json.data.payAmount).toFixed(2);
            if (json.data.paid) {
                clearPayNo();
                location.hash = '#/my-orders';
            } else {
                // 从支付宝返回时异步通知可能还没到，本页也轮询一次，避免停在“待支付”
                startPollingPayStatus();
            }
        } else if (json.msg) {
            alert(json.msg);
            location.hash = '#/my-orders';
        }
    }catch(e){ console.error('[order-pay] 加载失败：', e); }
}

// 支付宝支付：直接在新窗口打开后端生成支付表单页
async function alipayPay(){
    const payNo = getPayNo();
    if(!payNo) return;
    // 先把批次号写入 sessionStorage，window.open 的新标签页会复制一份
    rememberPayNo(payNo);
    // 后端返回的是 HTML 表单，会自动跳转到支付宝沙箱收银台
    window.open(`/api/order/alipay/page?payNo=${payNo}`, '_blank');
    // 打开后提示用户，并开始轮询支付状态
    alert('已打开支付宝收银台，请完成支付。支付完成后本页会自动刷新状态。');
    startPollingPayStatus();
}

let pollTimer = null;

function stopPolling(){
    if (pollTimer){ clearInterval(pollTimer); pollTimer = null; }
}

function onPaySuccess(){
    stopPolling();
    clearPayNo();
    localStorage.removeItem('payOrderNo');
    localStorage.removeItem('payAmount');
    // 支付成功,若购买的是 VIP 商品,刷新 VIP 状态缓存
    if (typeof Vip !== 'undefined') Vip.refresh();
    alert('✅ 支付完成');
    location.hash = '#/my-orders';
}
function startPollingPayStatus(){
    const payNo = getPayNo();
    if(!payNo) return;
    stopPolling();
    pollTimer = setInterval(async ()=>{
        try{
            // 支付页用 payNo（批次号），应调 pay-info 而非 detail（detail 要 orderNo）
            const json = await request(`/api/order/pay-info?payNo=${payNo}`);
            if(json.success && json.data && json.data.paid){
                onPaySuccess();
            }
        }catch (e) {
            console.error('[order-pay] 轮询失败', e);
        }
    },3000);// 每 3 秒查一次

    document.addEventListener('visibilitychange', async () => {
        if (document.visibilityState === 'visible' && pollTimer) {
            // 从支付宝标签页切回来，立即查一次
            try {
                const payNo = getPayNo();
                if (!payNo) return;
                const json = await request(`/api/order/pay-info?payNo=${payNo}`);
                if (json.success && json.data && json.data.paid) onPaySuccess();
            } catch (e) {}
        }
    });

}
// 离开页面前清掉轮询
window.addEventListener('hashchange', () => {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
});

//模拟支付
async function mockPay(){
    const payNo = getPayNo();
    if(!payNo) return;

    const btn = document.getElementById('payMockBtn');
    btn.disabled = true;
    btn.innerText='支付中...';
    try{
        const json = await request(`/api/order/pay?payNo=${payNo}&payType=mock`,{
            method:"POST"
        });
        if(json.success){
            onPaySuccess();
        }else{
            alert(json.msg || '支付失败');
            btn.disabled = false;
            btn.innerText= '模拟支付（点我直接成功）';
        }
    }catch (e) {
        console.error('[order-pay] 支付失败：', e);
        alert('网络异常');
        btn.disabled = false;
        btn.innerText = '模拟支付（点我直接成功）';
    }
}

//取消订单
async function cancelOrder(){
    const payNo = getPayNo();
    if(!payNo) return;

    if(!confirm('确定取消该订单吗？')) return;
    try{
        // payNo 是批次号，可能含多个订单；先拿订单号列表再逐个取消
        const info = await request(`/api/order/pay-info?payNo=${payNo}`);
        if(!info.success || !info.data || !info.data.orderNos || info.data.orderNos.length === 0){
            alert('订单信息异常，无法取消');
            return;
        }
        let allOk = true;
        let firstMsg = '';
        for(const no of info.data.orderNos){
            const res = await request(`/api/order/cancel?orderNo=${no}`,{ method:'POST' });
            if(!res.success){
                allOk = false;
                if(!firstMsg) firstMsg = res.msg || '取消失败';
            }
        }
        if(allOk){
            alert('订单已取消');
            clearPayNo();
            localStorage.removeItem('payOrderNo');
            localStorage.removeItem('payAmount');
            location.hash = '#/my-orders';
        }else{
            alert(firstMsg);
        }
    }catch (e) {
        console.error('[order-pay] 取消失败：', e);
        alert('网络异常');
    }
}

//绑定事件
let orderPayBound = false;
function bindOrderPayEvents(){
    if(orderPayBound) return;
    orderPayBound = true;

    const payBtn =  document.getElementById('payAlipayBtn');
    if(payBtn) payBtn.onclick= alipayPay;

    const cancelBtn = document.getElementById('payCancelBtn');
    if(cancelBtn) cancelBtn.onclick = cancelOrder;
}

//注册
registerPageHook('order-pay',async ()=>{
    bindOrderPayEvents();
    await loadPayPage();
})