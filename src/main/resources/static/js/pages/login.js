// js/pages/login.js
// 登录页业务逻辑：密码登录 / 短信登录 / 忘记密码

const PHONE_REGEX = /^1[3-9]\d{9}$/;

let loginEventsBound = false;

// 切换面板：pwd 密码登录 / sms 短信登录 / reset 忘记密码
function showLoginMode(mode) {
    document.querySelectorAll('.lc-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.mode === mode);
    });
    ['pwd', 'sms', 'reset'].forEach(m => {
        const view = document.getElementById(m + 'View');
        if (view) view.style.display = m === mode ? 'flex' : 'none';
    });
}

// 登录成功后：刷新顶部用户信息 + 建立站内消息 WebSocket
function refreshAfterLogin() {
    if (typeof renderUserHeader === 'function') renderUserHeader();
    if (typeof initChatClient === 'function') initChatClient();
}

async function postJson(url, body) {
    const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        credentials: 'include'
    });
    return res.json();
}

// 获取验证码（登录和忘记密码共用）
async function requestCode(btn) {
    const input = document.getElementById(btn.dataset.phone);
    const phone = input ? input.value.trim() : '';
    if (!PHONE_REGEX.test(phone)) {
        alert('请输入正确的手机号');
        return;
    }
    if (btn.disabled) return;

    try {
        const res = await fetch('/api/user/code?phone=' + encodeURIComponent(phone), {
            method: 'POST',
            credentials: 'include'
        });
        const json = await res.json();
        if (!json.success) {
            alert(json.msg || '验证码发送失败');
            return;
        }
        // 演示环境没有接入真实短信服务，验证码随响应返回，直接提示出来
        alert('验证码已发送：' + json.data);
        startSendCodeCountdown(btn);
    } catch (err) {
        console.error('[login] 发送验证码失败：', err);
        alert('网络请求失败，请检查后端服务');
    }
}

// 发送按钮 60 秒倒计时（后端同样有 60 秒冷却）
function startSendCodeCountdown(btn) {
    if (btn._timer) clearInterval(btn._timer);
    let left = 60;
    btn.disabled = true;
    btn.innerText = left + 's 后重发';
    btn._timer = setInterval(() => {
        left--;
        if (left <= 0) {
            clearInterval(btn._timer);
            btn._timer = null;
            btn.disabled = false;
            btn.innerText = '获取验证码';
        } else {
            btn.innerText = left + 's 后重发';
        }
    }, 1000);
}

function bindLoginEvents() {
    if (loginEventsBound) return;
    loginEventsBound = true;

    // ---- 面板切换（顶部 tab + 忘记密码 / 返回登录） ----
    document.querySelectorAll('[data-mode]').forEach(el => {
        el.onclick = () => {
            // 从密码登录进忘记密码时，把已填的手机号带过去
            if (el.dataset.mode === 'reset') {
                const from = document.getElementById('loginPhone').value.trim();
                if (from) document.getElementById('resetPhone').value = from;
            }
            showLoginMode(el.dataset.mode);
        };
    });

    // ---- 密码登录 ----
    const loginBtn = document.getElementById('loginSubmit');
    if (loginBtn) {
        loginBtn.onclick = async function () {
            const phone = (document.getElementById('loginPhone').value || '').trim();
            const password = (document.getElementById('loginPassword').value || '').trim();

            if (!phone || !password) {
                alert('手机号和密码不能为空');
                return;
            }
            if (!PHONE_REGEX.test(phone)) {
                alert('请输入正确的手机号');
                return;
            }

            try {
                const json = await postJson('/api/user/login', { phone, password });
                if (json.success) {
                    alert(json.msg || '登录成功');
                    location.hash = '#/home';
                    refreshAfterLogin();
                } else {
                    alert(json.msg || '登录失败');
                }
            } catch (err) {
                console.error('[login] 登录失败：', err);
                alert('网络请求失败，请检查后端服务');
            }
        };
    }

    // ---- 获取验证码 ----
    const sendBtn = document.getElementById('sendCodeBtn');
    if (sendBtn) sendBtn.onclick = () => requestCode(sendBtn);
    const resetSendBtn = document.getElementById('resetSendBtn');
    if (resetSendBtn) resetSendBtn.onclick = () => requestCode(resetSendBtn);

    // ---- 验证码登录 ----
    const smsBtn = document.getElementById('smsLoginSubmit');
    if (smsBtn) {
        smsBtn.onclick = async function () {
            const phone = (document.getElementById('smsPhone').value || '').trim();
            const code = (document.getElementById('smsCode').value || '').trim();

            if (!phone || !code) {
                alert('手机号和验证码不能为空');
                return;
            }
            if (!PHONE_REGEX.test(phone)) {
                alert('请输入正确的手机号');
                return;
            }
            if (!/^\d{6}$/.test(code)) {
                alert('请输入 6 位数字验证码');
                return;
            }

            try {
                const json = await postJson('/api/user/loginbaseCode', { phone, code });
                if (json.success) {
                    alert(json.msg || '登录成功');
                    location.hash = '#/home';
                    refreshAfterLogin();
                } else {
                    alert(json.msg || '登录失败');
                }
            } catch (err) {
                console.error('[login] 验证码登录失败：', err);
                alert('网络请求失败，请检查后端服务');
            }
        };
    }

    // ---- 忘记密码：确认修改 ----
    const resetBtn = document.getElementById('resetSubmit');
    if (resetBtn) {
        resetBtn.onclick = async function () {
            const phone = (document.getElementById('resetPhone').value || '').trim();
            const code = (document.getElementById('resetCode').value || '').trim();
            const password = (document.getElementById('resetPassword').value || '').trim();
            const confirm = (document.getElementById('resetConfirm').value || '').trim();

            if (!phone || !code || !password || !confirm) {
                alert('请填写完整信息');
                return;
            }
            if (!PHONE_REGEX.test(phone)) {
                alert('请输入正确的手机号');
                return;
            }
            if (!/^\d{6}$/.test(code)) {
                alert('请输入 6 位数字验证码');
                return;
            }
            if (password.length < 6) {
                alert('密码长度至少 6 位');
                return;
            }
            if (password !== confirm) {
                alert('两次输入的新密码不一致');
                return;
            }

            try {
                const json = await postJson('/api/user/resetPassword', { phone, code, password });
                if (json.success) {
                    alert(json.msg || '密码修改成功，请重新登录');
                    ['resetPhone', 'resetCode', 'resetPassword', 'resetConfirm'].forEach(id => {
                        document.getElementById(id).value = '';
                    });
                    showLoginMode('pwd');
                } else {
                    alert(json.msg || '修改失败');
                }
            } catch (err) {
                console.error('[login] 重置密码失败：', err);
                alert('网络请求失败，请检查后端服务');
            }
        };
    }

    // 回车提交
    ['loginPhone', 'loginPassword'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.onkeydown = e => {
                if (e.key === 'Enter' && loginBtn) loginBtn.click();
            };
        }
    });
    ['smsPhone', 'smsCode'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.onkeydown = e => {
                if (e.key === 'Enter' && smsBtn) smsBtn.click();
            };
        }
    });
    ['resetPhone', 'resetCode', 'resetPassword', 'resetConfirm'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.onkeydown = e => {
                if (e.key === 'Enter' && resetBtn) resetBtn.click();
            };
        }
    });
}

// ========== 登录页钩子 ==========
registerPageHook('login', async () => {
    // 清空输入框（防止切回来残留上次输入）
    ['loginPhone', 'loginPassword', 'smsPhone', 'smsCode',
        'resetPhone', 'resetCode', 'resetPassword', 'resetConfirm'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    showLoginMode('pwd');
    bindLoginEvents();
});
