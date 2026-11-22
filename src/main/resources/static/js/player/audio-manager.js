//只放 AudioManager
// js/player/audio-manager.js
// 播放器核心：只管播放逻辑和状态，不碰 DOM
// 通过事件通知 UI 层更新界面

// 播放模式枚举
const PLAY_MODE = {
    LIST_LOOP: 0,    // 列表循环
    SINGLE_LOOP: 1,  // 单曲循环
    RANDOM: 2        // 随机播放
};

// ==================== AudioManager ====================
const AudioManager = {
    // ---------- 状态 ----------
    audio: null,              // <audio> 元素
    currentSong: null,        // 当前歌曲对象
    songQueue: [],            // 播放队列
    currentIndex: 0,          // 当前歌曲在队列中的下标
    playMode: PLAY_MODE.LIST_LOOP,
    lyricList: [],            // 当前歌曲的歌词数组
    isPlaying: false,         // 是否正在播放（不依赖 audio.paused）
    volume: 0.7,              // 音量（默认 0.7）

    // ---------- 事件订阅 ----------
    _listeners: {},           // { eventName: [callback, ...] }

    on(event, callback) {
        if (!this._listeners[event]) this._listeners[event] = [];
        this._listeners[event].push(callback);
    },

    emit(event, data) {
        const cbs = this._listeners[event] || [];
        cbs.forEach(cb => {
            try {
                cb(data);
            } catch (err) {
                console.error(`[AudioManager] 事件 ${event} 回调异常：`, err);
            }
        });
    },

    // ---------- 初始化 ----------
    init(audioEl) {
        this.audio = audioEl;

        // 读取本地存储的播放模式
        const savedMode = localStorage.getItem('playMode');
        if (savedMode != null) {
            this.playMode = Number(savedMode);
        }

        // 读取本地存储的音量
        const savedVol = localStorage.getItem('playerVolume');
        if (savedVol != null) {
            this.volume = Number(savedVol);
        }
        this.audio.volume = this.volume;

        // ---------- 绑定 audio 原生事件 ----------
        // 时间更新：通知 UI 更新进度条 + 歌词;试听模式 30 秒截停
        this.audio.addEventListener('timeupdate', () => {
            if (this._previewLimit && this.audio.currentTime >= this._previewLimit) {
                this.pause();
                this.emit('vip-preview-end', this.currentSong);
                return;
            }
            this.emit('timeupdate', {
                current: this.audio.currentTime,
                total: this.audio.duration || 0
            });
        });

        // 加载完成：通知 UI 更新总时长
        this.audio.addEventListener('loadedmetadata', () => {
            this.emit('loadedmetadata', {
                duration: this.audio.duration
            });
        });

        // 播放结束：自动切下一首
        this.audio.addEventListener('ended', () => {
            this.nextSong();
        });

        // 播放状态变化：通知 UI
        this.audio.addEventListener('play', () => {
            this.isPlaying = true;
            this.emit('playstate', { playing: true });
        });
        this.audio.addEventListener('pause', () => {
            this.isPlaying = false;
            this.emit('playstate', { playing: false });
        });

        console.log('[AudioManager] 初始化完成');
        this.emit('init');
    },

    // ---------- 播放一首歌 ----------
    async play(song) {
        if (!song.songId && song.id) {
            song = { ...song, songId: song.id };
        }
        if (!song || !song.audioUrl) {
            console.warn('[AudioManager] 歌曲无音频资源：', song);
            alert('该歌曲暂无音频资源');
            return Promise.reject('音频地址为空');
        }
        // VIP 歌曲 + 非 VIP 用户 → 30 秒试听
        if (typeof Vip !== 'undefined' && Vip.isVipSong(song) && !(await Vip.isVip())) {
            this._previewLimit = 30;
        } else {
            this._previewLimit = null;
        }
        this.currentSong = song;
        this.audio.src = song.audioUrl;

        // 通知 UI：歌曲切换
        this.emit('songchange', song);

        try {
            await this.audio.play();
            this.isPlaying = true;
            this.emit('playstate', { playing: true });

            // 上报播放历史和播放量
            this.reportPlay(song);
        } catch (err) {
            console.warn('[AudioManager] 自动播放被拒绝：', err);
            this.isPlaying = false;
            this.emit('playstate', { playing: false });
        }
    },

    // ---------- 暂停 ----------
    pause() {
        this.audio.pause();
        this.isPlaying = false;
    },

    // ---------- 继续播放 ----------
    async resume() {
        if (!this.currentSong) return;
        try {
            await this.audio.play();
            this.isPlaying = true;
        } catch (err) {
            console.warn('[AudioManager] 播放失败：', err);
        }
    },

    // ---------- 下一首 ----------
    async nextSong() {
        const queue = this.songQueue;
        if (!queue || queue.length === 0) return;

        let nextIdx;
        switch (this.playMode) {
            case PLAY_MODE.SINGLE_LOOP:
                nextIdx = this.currentIndex;
                break;
            case PLAY_MODE.RANDOM:
                if (queue.length === 1) {
                    nextIdx = 0;
                } else {
                    do {
                        nextIdx = Math.floor(Math.random() * queue.length);
                    } while (nextIdx === this.currentIndex);
                }
                break;
            case PLAY_MODE.LIST_LOOP:
            default:
                nextIdx = this.currentIndex + 1;
                if (nextIdx >= queue.length) nextIdx = 0;
                break;
        }
        this.currentIndex = nextIdx;
        await this.play(queue[nextIdx]);
    },

    // ---------- 上一首 ----------
    async prevSong() {
        const queue = this.songQueue;
        if (!queue || queue.length === 0) return;

        let prevIdx;
        switch (this.playMode) {
            case PLAY_MODE.SINGLE_LOOP:
                prevIdx = this.currentIndex;
                break;
            case PLAY_MODE.RANDOM:
                if (queue.length === 1) {
                    prevIdx = 0;
                } else {
                    do {
                        prevIdx = Math.floor(Math.random() * queue.length);
                    } while (prevIdx === this.currentIndex);
                }
                break;
            case PLAY_MODE.LIST_LOOP:
            default:
                prevIdx = this.currentIndex - 1;
                if (prevIdx < 0) prevIdx = queue.length - 1;
                break;
        }
        this.currentIndex = prevIdx;
        await this.play(queue[prevIdx]);
    },

    // ---------- 设置播放队列 ----------
    setQueue(songList, songId) {
        this.songQueue = songList || [];
        const idx = this.songQueue.findIndex(s => s.id === songId);
        this.currentIndex = idx >= 0 ? idx : 0;
    },

    // ---------- 通过歌曲 ID 加载并播放 ----------
    async loadSongById(songId) {
        try {
            const json = await request(`/api/song/getById?id=${songId}`);
            if (!json.success || !json.data) {
                alert(json.msg || '加载歌曲失败');
                return;
            }
            const song = json.data;

            // 如果队列是空的，就把这首歌当作唯一队列
            if (this.songQueue.length === 0) {
                this.songQueue = [song];
                this.currentIndex = 0;
            } else {
                // 如果这首歌不在队列里，就替换队列为单曲
                const idx = this.songQueue.findIndex(s => s.id === songId);
                if (idx === -1) {
                    this.songQueue = [song];
                    this.currentIndex = 0;
                } else {
                    this.currentIndex = idx;
                }
            }

            await this.play(song);
        } catch (err) {
            console.error('[AudioManager] 加载歌曲失败：', err);
        }
    },

    // ---------- 设置播放模式 ----------
    setPlayMode(mode) {
        this.playMode = mode;
        localStorage.setItem('playMode', mode);
        this.emit('modechange', mode);
    },

    // ---------- 设置音量 ----------
    setVolume(v) {
        this.volume = v;
        if (this.audio) this.audio.volume = v;
        localStorage.setItem('playerVolume', v);
    },

    // ---------- 跳转进度 ----------
    seek(time) {
        // 试听模式:禁止拖过试听上限
        if (this._previewLimit && time > this._previewLimit) {
            time = this._previewLimit - 1;
        }
        if (this.audio) this.audio.currentTime = time;
    },

    // ---------- 上报播放历史 + 播放量 ----------
    _lastReportSongId: null,
    _lastReportTime: 0,
    reportPlay(song) {
        const now = Date.now();
        // 同一首歌 30 秒内不重复上报
        if (this._lastReportSongId === song.songId && now - this._lastReportTime < 30000) {
            return;
        }
        // 播放量 +1（GET）
        request(`/api/song/addPlayCount?id=${song.songId}`)
            .catch(err => console.warn('[AudioManager] 播放量上报失败：', err));
        // 播放历史（POST）
        request(`/api/song_history/add?songId=${song.songId}`, { method: 'POST' })
            .then(() => {
                this._lastReportSongId = song.songId;
                this._lastReportTime = now;
            })
            .catch(err => console.warn('[AudioManager] 历史上报失败：', err));
    }
};