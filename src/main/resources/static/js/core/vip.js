// js/core/vip.js
// VIP 状态判断:缓存式查询当前用户 VIP 权益
const Vip = {
    _promise: null,

    // 查询当前用户是否 VIP(结果缓存,未登录/异常按非 VIP 处理)
    async isVip() {
        if (!this._promise) {
            this._promise = fetch('/api/user/getUserInfo', { credentials: 'include' })
                .then(res => res.json())
                .then(json => !!(json.success && json.data && json.data.vip))
                .catch(() => false);
        }
        return this._promise;
    },

    // 购买 VIP 后调用,清缓存重新拉取
    refresh() {
        this._promise = null;
    },

    // 判断歌曲是否 VIP 专属
    isVipSong(song) {
        return !!song && (song.isVip === 1 || song.isVip === true);
    }
};
