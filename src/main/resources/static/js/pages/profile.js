// js/pages/profile.js
// 个人资料编辑 + 账号安全

(function () {
    let profile = null;
    let currentAvatar = '';
    let pwdMode = 'change';
    let bound = false;
    let smsTimer = null;

    function fillForm() {
        if (!profile) return;
        document.getElementById('peNickname').value = profile.nickname || '';
        document.getElementById('pePhone').value = profile.phone || '';
        document.getElementById('peBirthday').value = profile.birthday || '';
        document.getElementById('peSignature').value = profile.signature || '';
        currentAvatar = profile.avatar || '';

        const avatar = document.getElementById('peAvatar');
        if (profile.avatar) avatar.src = profile.avatar;

        const gender = Number(profile.gender || 0);
        document.querySelectorAll('input[name="peGender"]').forEach(el => {
            el.checked = Number(el.value) === gender;
        });
        renderPasswordArea();
    }

    function renderPasswordArea() {
        const box = document.getElementById('pePasswordArea');
        if (!box) return;
        if (Number(profile.passwordSet || 0) === 0) {
            box.innerHTML = `
                <div class="pe-tip">该账号通过短信登录创建，还没有设置密码。设置后可以使用手机号 + 密码登录。</div>
                <button type="button" class="pe-outline-btn" id="peSetPwdBtn">通过短信设置密码</button>`;
            document.getElementById('peSetPwdBtn').onclick = () => openPwdModal('set');
        } else {
            box.innerHTML = `
                <div class="pe-tip">修改密码需要验证原密码，修改成功后需要重新登录。</div>
                <button type="button" class="pe-outline-btn" id="peChangePwdBtn">修改密码</button>`;
            document.getElementById('peChangePwdBtn').onclick = () => openPwdModal('change');
        }
    }

    async function loadProfile() {
        try {
            const json = await request('/api/user/profile');
            profile = json.data;
            fillForm();
        } catch (e) {
            alert(e.message || '资料加载失败');
        }
    }

    function ensurePwdModal() {
        if (document.getElementById('pePwdModal')) return;
        const modal = document.createElement('div');
        modal.id = 'pePwdModal';
        modal.className = 'pe-modal';
        modal.innerHTML = `
            <div class="pe-modal-box">
                <div class="pe-modal-head">
                    <span id="pePwdTitle">修改密码</span>
                    <button type="button" id="pePwdClose">×</button>
                </div>
                <div id="pePwdBody"></div>
                <div class="pe-modal-actions">
                    <button type="button" class="pe-cancel-btn" id="pePwdCancel">取消</button>
                    <button type="button" class="pe-confirm-btn" id="pePwdSubmit">确认</button>
                </div>
            </div>`;
        document.body.appendChild(modal);

        const close = () => {
            modal.classList.remove('show');
            if (smsTimer) { clearInterval(smsTimer); smsTimer = null; }
        };
        modal.querySelector('#pePwdClose').onclick = close;
        modal.querySelector('#pePwdCancel').onclick = close;
        modal.addEventListener('click', e => { if (e.target === modal) close(); });
        modal.querySelector('#pePwdSubmit').onclick = submitPassword;
    }

    function openPwdModal(mode) {
        ensurePwdModal();
        pwdMode = mode;
        const title = document.getElementById('pePwdTitle');
        const body = document.getElementById('pePwdBody');
        const modal = document.getElementById('pePwdModal');

        if (mode === 'set') {
            title.textContent = '设置密码';
            body.innerHTML = `
                <div class="pe-field"><label>手机号</label>
                    <input type="text" id="peSmsPhone" readonly value="${escapeHtml(profile.phone || '')}">
                </div>
                <div class="pe-field"><label>验证码</label>
                    <div class="pe-code-row">
                        <input type="text" id="peSmsCode" maxlength="6" placeholder="6 位验证码">
                        <button type="button" class="pe-code-btn" id="peSendCodeBtn">获取验证码</button>
                    </div>
                </div>
                <div class="pe-field"><label>新密码</label>
                    <input type="password" id="peNewPwd" maxlength="20" placeholder="6-20 位">
                </div>
                <div class="pe-field"><label>确认新密码</label>
                    <input type="password" id="peNewConfirm" maxlength="20">
                </div>`;
            document.getElementById('peSendCodeBtn').onclick = sendSmsCode;
        } else {
            title.textContent = '修改密码';
            body.innerHTML = `
                <div class="pe-field"><label>原密码</label>
                    <input type="password" id="peOldPwd" maxlength="20">
                </div>
                <div class="pe-field"><label>新密码</label>
                    <input type="password" id="peNewPwd" maxlength="20" placeholder="6-20 位">
                </div>
                <div class="pe-field"><label>确认新密码</label>
                    <input type="password" id="peNewConfirm" maxlength="20">
                </div>`;
        }
        modal.classList.add('show');
    }

    async function sendSmsCode() {
        const btn = document.getElementById('peSendCodeBtn');
        const phone = document.getElementById('peSmsPhone').value.trim();
        if (!phone) return;
        btn.disabled = true;
        try {
            const json = await request(`/api/user/code?phone=${encodeURIComponent(phone)}`, { method: 'POST' });
            alert(json.data ? `验证码已发送：${json.data}` : '验证码已发送');
            let left = 60;
            btn.textContent = `${left}s`;
            smsTimer = setInterval(() => {
                left--;
                if (left <= 0) {
                    clearInterval(smsTimer);
                    smsTimer = null;
                    btn.disabled = false;
                    btn.textContent = '获取验证码';
                } else {
                    btn.textContent = `${left}s`;
                }
            }, 1000);
        } catch (e) {
            btn.disabled = false;
            alert(e.message || '发送失败');
        }
    }

    async function submitPassword() {
        const newPwd = (document.getElementById('peNewPwd')?.value || '').trim();
        const confirmPwd = (document.getElementById('peNewConfirm')?.value || '').trim();
        if (newPwd.length < 6 || newPwd.length > 20) {
            alert('新密码长度需要 6-20 位');
            return;
        }
        if (newPwd !== confirmPwd) {
            alert('两次输入的新密码不一致');
            return;
        }

        const btn = document.getElementById('pePwdSubmit');
        btn.disabled = true;
        try {
            if (pwdMode === 'set') {
                const phone = document.getElementById('peSmsPhone').value.trim();
                const code = (document.getElementById('peSmsCode')?.value || '').trim();
                if (!code) { alert('请输入验证码'); return; }
                await request('/api/user/resetPassword', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ phone, code, password: newPwd })
                });
                document.getElementById('pePwdModal').classList.remove('show');
                alert('密码设置成功');
                await loadProfile();
            } else {
                const oldPwd = (document.getElementById('peOldPwd')?.value || '').trim();
                if (!oldPwd) { alert('请输入原密码'); return; }
                await request('/api/user/changePassword', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ oldPassword: oldPwd, newPassword: newPwd })
                });
                document.getElementById('pePwdModal').classList.remove('show');
                alert('密码修改成功，请重新登录');
                location.hash = '#/login';
                location.reload();
            }
        } catch (e) {
            alert(e.message || '操作失败');
        } finally {
            btn.disabled = false;
        }
    }

    async function saveProfile() {
        const nickname = (document.getElementById('peNickname').value || '').trim();
        if (nickname.length < 2 || nickname.length > 20) {
            alert('昵称长度需要 2-20 个字符');
            return;
        }
        const gender = Number(document.querySelector('input[name="peGender"]:checked')?.value || 0);
        const btn = document.getElementById('peSaveBtn');
        btn.disabled = true;
        btn.textContent = '保存中...';
        try {
            await request('/api/user/profile/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    nickname,
                    avatar: currentAvatar,
                    gender,
                    birthday: document.getElementById('peBirthday').value || '',
                    signature: (document.getElementById('peSignature').value || '').trim()
                })
            });
            alert('保存成功');
            location.hash = '#/user';
        } catch (e) {
            alert(e.message || '保存失败');
        } finally {
            btn.disabled = false;
            btn.textContent = '保存资料';
        }
    }

    function compressImage(file) {
        return new Promise(resolve => {
            if (file.size <= 1024 * 1024) {
                resolve(file);
                return;
            }
            const url = URL.createObjectURL(file);
            const img = new Image();
            img.onload = () => {
                const max = 1024;
                let width = img.width;
                let height = img.height;
                if (width > max || height > max) {
                    const scale = max / Math.max(width, height);
                    width = Math.round(width * scale);
                    height = Math.round(height * scale);
                }
                const canvas = document.createElement('canvas');
                canvas.width = width;
                canvas.height = height;
                canvas.getContext('2d').drawImage(img, 0, 0, width, height);
                canvas.toBlob(blob => {
                    URL.revokeObjectURL(url);
                    resolve(blob || file);
                }, 'image/jpeg', 0.88);
            };
            img.onerror = () => {
                URL.revokeObjectURL(url);
                resolve(file);
            };
            img.src = url;
        });
    }

    function bindOnce() {
        if (bound) return;
        bound = true;

        document.getElementById('peAvatarBtn').onclick = () => {
            document.getElementById('peAvatarInput').click();
        };
        document.getElementById('peAvatarInput').onchange = async e => {
            const file = (e.target.files || [])[0];
            e.target.value = '';
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                alert('请选择图片文件');
                return;
            }
            if (file.size > 10 * 1024 * 1024) {
                alert('图片不能超过 10MB，请先压缩后再上传');
                return;
            }

            const tip = document.getElementById('peAvatarTip');
            try {
                // 大图先压缩，避免手机截图直接超过上传限制
                const blob = await compressImage(file);
                if (blob.size > 5 * 1024 * 1024) {
                    alert('图片压缩后仍超过 5MB，请换一张图片');
                    return;
                }
                const form = new FormData();
                form.append('file', blob, 'avatar.jpg');

                const json = await request('/api/user/avatar', { method: 'POST', body: form });
                const url = json.data;

                // 头像上传成功后立即写库，不需要再点保存
                await request('/api/user/profile/update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ avatar: url })
                });

                currentAvatar = url;
                if (profile) profile.avatar = url;
                document.getElementById('peAvatar').src = url;
                if (tip) tip.textContent = '头像已更新';
            } catch (err) {
                alert(err.message || '头像上传失败');
            }
        };
        document.getElementById('peSaveBtn').onclick = saveProfile;
    }

    registerPageHook('profile-edit', async () => {
        bindOnce();
        await loadProfile();
    });
})();
