// js/player/player-ui.js
// 播放器 UI 层：只负责更新界面 + 绑定事件，不碰 audio 元素

const PlayerUI = {
    // DOM 缓存
    els: {},

    // 标记：是否是"首次播放"（只有首次播放才自动跳播放页）
    _firstPlay: true,

    // ---------- 初始化 ----------
    init() {
        // 1. 缓存所有 DOM
        this.els = {
            // ===== 播放页元素 =====
            mpCover: document.getElementById('mpCover'),
            mpSongName: document.getElementById('mpSongName'),
            mpSinger: document.getElementById('mpSinger'),

            playBtn: document.getElementById('playBtn'),
            prevBtn: document.getElementById('prevBtn'),
            nextBtn: document.getElementById('nextBtn'),
            progressBar: document.getElementById('progressBar'),
            currentTime: document.getElementById('currentTime'),
            totalTime: document.getElementById('totalTime'),
            lyricWrap: document.getElementById('lyricWrap'),

            modeBtn: document.getElementById('modeBtn'),
            modeMenu: document.getElementById('modeMenu'),
            modeItems: document.querySelectorAll('#modeMenu .mode-item'),

            volumeSlider: document.getElementById('volumeSlider'),
            volumeIcon: document.getElementById('volumeIcon'),

            collectBtn: document.getElementById('collectBtn'),
            collectHeart: document.getElementById('collectHeart'),

            playlistBtn: document.getElementById('playlistBtn'),
            playlistPanel: document.getElementById('playlistPanel'),
            playlistClose: document.getElementById('playlistClose'),
            playlistBody: document.getElementById('playlistBody'),
            playlistCount: document.getElementById('playlistCount'),

            // ===== 底部播放栏 =====
            bottomBar: document.getElementById('bottomPlayerBar'),
            bottomOpenArea: document.getElementById('bottomOpenArea'),
            bottomCover: document.getElementById('bottomCover'),
            bottomSongName: document.getElementById('bottomSongName'),
            bottomSinger: document.getElementById('bottomSinger'),
            playPauseBtn: document.getElementById('playPauseBtn')
        };

        // 2. 初始化播放模式按钮的文字
        this.updateModeUI(AudioManager.playMode);

        // 3. 初始化音量
        if (this.els.volumeSlider) this.els.volumeSlider.value = AudioManager.volume;
        this.updateVolumeIcon(AudioManager.volume);

        // 4. 绑定事件
        this.bindEvents();

        // 5. 订阅 AudioManager 事件
        this.subscribeAudioManager();

        console.log('[PlayerUI] 初始化完成');
    },

    // ---------- 绑定 UI 事件 ----------
    bindEvents() {
        const e = this.els;

        // ---- 播放/暂停（播放页中间按钮）----
        if (e.playBtn) {
            e.playBtn.onclick = () => {
                if (AudioManager.isPlaying) AudioManager.pause();
                else AudioManager.resume();
            };
        }

        // ---- 播放/暂停（底部栏按钮）----
        if (e.playPauseBtn) {
            e.playPauseBtn.onclick = () => {
                if (AudioManager.isPlaying) AudioManager.pause();
                else AudioManager.resume();
            };
        }

        // ---- 上一首/下一首 ----
        if (e.prevBtn) e.prevBtn.onclick = () => AudioManager.prevSong();
        if (e.nextBtn) e.nextBtn.onclick = () => AudioManager.nextSong();

        // ---- 进度条拖动 ----
        if (e.progressBar) {
            e.progressBar.oninput = () => {
                AudioManager.seek(Number(e.progressBar.value));
            };
        }

        // ---- 音量 ----
        if (e.volumeSlider) {
            e.volumeSlider.oninput = () => {
                const v = Number(e.volumeSlider.value);
                AudioManager.setVolume(v);
                this.updateVolumeIcon(v);
            };
        }
        if (e.volumeIcon) {
            e.volumeIcon.onclick = () => {
                if (AudioManager.volume > 0) {
                    AudioManager.setVolume(0);
                    if (e.volumeSlider) e.volumeSlider.value = 0;
                    this.updateVolumeIcon(0);
                } else {
                    AudioManager.setVolume(0.7);
                    if (e.volumeSlider) e.volumeSlider.value = 0.7;
                    this.updateVolumeIcon(0.7);
                }
            };
        }

        // ---- 播放模式：点击按钮弹菜单 ----
        if (e.modeBtn) {
            e.modeBtn.onclick = (ev) => {
                ev.stopPropagation();
                if (e.modeMenu) {
                    const isOpen = e.modeMenu.style.display === 'flex';
                    e.modeMenu.style.display = isOpen ? 'none' : 'flex';
                }
            };
        }
        // 点菜单项切模式
        if (e.modeItems) {
            e.modeItems.forEach(item => {
                item.onclick = (ev) => {
                    ev.stopPropagation();
                    const mode = Number(item.dataset.mode);
                    AudioManager.setPlayMode(mode);
                    this.updateModeUI(mode);
                    if (e.modeMenu) e.modeMenu.style.display = 'none';
                };
            });
        }
        // 点其他地方关闭菜单
        document.addEventListener('click', () => {
            if (e.modeMenu) e.modeMenu.style.display = 'none';
        });

        // ---- 收藏 ----
        if (e.collectBtn) {
            e.collectBtn.onclick = async (evt) => {
                evt.preventDefault();   // 阻止 label 自行勾选,以接口结果为准
                if (!AudioManager.currentSong) return;
                const songId = AudioManager.currentSong.id;
                try {
                    const isCollect = await request(`/api/collect/isCollect?songId=${songId}`);
                    const collected = isCollect.success && isCollect.data;
                    const url = collected
                        ? `/api/collect/cancel?songId=${songId}`
                        : `/api/collect/add?songId=${songId}`;
                    const json = await request(url, { method: 'POST' });
                    if (json.success) {
                        this.updateCollectUI(!collected);
                    } else {
                        alert(json.msg || '操作失败');
                    }
                } catch (err) {
                    console.error('[PlayerUI] 收藏异常：', err);
                }
            };
        }

        // ---- 播放列表抽屉 ----
        if (e.playlistBtn) {
            e.playlistBtn.onclick = () => this.openPlaylist();
        }
        if (e.playlistClose) {
            e.playlistClose.onclick = () => this.closePlaylist();
        }

        // ---- 点底部播放栏的信息区 → 跳播放页 ----
        if (e.bottomOpenArea) {
            e.bottomOpenArea.onclick = () => {
                location.hash = '#/player';
            };
        }
    },

    // ---------- 订阅 AudioManager 的事件 ----------
    subscribeAudioManager() {
        // 切歌
        AudioManager.on('songchange', (song) => {
            this.updateCover(song);
            this.updateSongInfo(song);
            this.updateLyric(song.lyric || '');
            this.updateCollectUI(false);  // 默认未收藏，下面异步查
            this.showBottomBar();
            this.checkCollectStatus(song.id);

            // 关键：只在**第一次播放**时自动跳播放页，
            // 后续切歌不强制跳，避免用户主动返回后又被拉回
            if (this._firstPlay) {
                this._firstPlay = false;
                if (location.hash !== '#/player') {
                    location.hash = '#/player';
                }
            }

            // 如果播放列表打开，重新渲染
            if (this.els.playlistPanel && this.els.playlistPanel.classList.contains('open')) {
                this.renderPlaylist();
            }
        });

        // 播放状态变化
        AudioManager.on('playstate', ({ playing }) => {
            this.updatePlayState(playing);
        });

        // 进度更新
        AudioManager.on('timeupdate', ({ current, total }) => {
            this.updateProgress(current, total);
            this.updateLyricHighlight(current);
        });

        // 加载元数据（总时长）
        AudioManager.on('loadedmetadata', ({ duration }) => {
            // 试听模式:进度条上限显示为试听时长
            const limit = AudioManager._previewLimit;
            const showTotal = limit ? Math.min(limit, duration) : duration;
            if (this.els.totalTime) {
                this.els.totalTime.innerText = limit
                    ? formatTime(showTotal) + '(试听)'
                    : formatTime(showTotal);
            }
            if (this.els.progressBar) {
                this.els.progressBar.max = showTotal;
            }
        });

        // VIP 试听结束:引导开通
        AudioManager.on('vip-preview-end', () => {
            if (confirm('该歌曲为 VIP 专属,试听已结束。开通 VIP 即可畅听完整版,是否前往开通?')) {
                location.hash = '#/shop';
            }
        });
    },

    // ---------- 更新封面（播放页 + 底部栏）----------
    updateCover(song) {
        const cover = song.coverUrl || '';
        if (this.els.mpCover) {
            this.els.mpCover.src = cover;
        }
        if (this.els.bottomCover) {
            this.els.bottomCover.src = cover;
        }
    },

    // ---------- 更新歌名 + 歌手 ----------
    updateSongInfo(song) {
        if (this.els.mpSongName) this.els.mpSongName.innerText = song.songName || '-';
        if (this.els.mpSinger) this.els.mpSinger.innerText = song.singer || '-';
        if (this.els.bottomSongName) this.els.bottomSongName.innerText = song.songName || '-';
        if (this.els.bottomSinger) this.els.bottomSinger.innerText = song.singer || '-';
    },

    // ---------- 更新播放/暂停状态 ----------
    // ---------- 更新播放/暂停状态 ----------
    updatePlayState(playing) {
        // 1. 获取两个按钮
        const playBtn = this.els.playBtn;
        const playPauseBtn = this.els.playPauseBtn;

        // 2. 定义一个更新单个开关的函数
        const updateSwitch = (btn) => {
            if (!btn) return;
            const titleEl = btn.querySelector('.switch-title');
            const iconEl = btn.querySelector('.icon');

            if (playing) {
                btn.classList.add('playing');
                if (titleEl) titleEl.innerText = '暂停';
                if (iconEl) iconEl.innerText = '⏸'; // 播放时显示暂停符号
            } else {
                btn.classList.remove('playing');
                if (titleEl) titleEl.innerText = '播放';
                if (iconEl) iconEl.innerText = '▶'; // 暂停时显示播放符号
            }
        };

        // 3. 更新播放页的大按钮
        updateSwitch(playBtn);

        // 4. 更新底部的长条按钮
        updateSwitch(playPauseBtn);
    },

    // ---------- 更新进度条 + 时间 ----------
    updateProgress(current, total) {
        if (this.els.progressBar) this.els.progressBar.value = current;
        if (this.els.currentTime) this.els.currentTime.innerText = formatTime(current);
        if (total && this.els.totalTime) this.els.totalTime.innerText = formatTime(total);
    },

    // ---------- 更新歌词列表 ----------
    updateLyric(lrcStr) {
        const wrap = this.els.lyricWrap;
        if (!wrap) return;

        const list = parseLrc(lrcStr);
        AudioManager.lyricList = list;

        wrap.innerHTML = '';
        if (list.length === 0) {
            const div = document.createElement('div');
            div.className = 'lyric-line';
            div.innerText = '暂无歌词';
            wrap.appendChild(div);
            return;
        }
        list.forEach(item => {
            const div = document.createElement('div');
            div.className = 'lyric-line';
            div.innerText = item.text;
            div.onclick = () => {
                AudioManager.seek(item.time);
                if (!AudioManager.isPlaying) AudioManager.resume();
            };
            wrap.appendChild(div);
        });
        this._lastLyricIndex = -1;  // 重置高亮状态
    },

    // ---------- 歌词高亮滚动 ----------
    _lastLyricIndex: -1,
    updateLyricHighlight(currentTime) {
        const wrap = this.els.lyricWrap;
        const list = AudioManager.lyricList;
        if (!wrap || !list || list.length === 0) return;

        let activeIndex = 0;
        for (let i = 0; i < list.length; i++) {
            if (currentTime >= list[i].time) activeIndex = i;
            else break;
        }
        if (activeIndex === this._lastLyricIndex) return;
        this._lastLyricIndex = activeIndex;

        const lines = wrap.querySelectorAll('.lyric-line');
        lines.forEach((line, idx) => {
            line.classList.toggle('active', idx === activeIndex);
        });
        if (lines[activeIndex]) {
            lines[activeIndex].scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    },

    // ---------- 更新播放模式按钮 ----------
    updateModeUI(mode) {
        const texts = ['🔁', '🔂', '🎲'];
        if (this.els.modeBtn) this.els.modeBtn.innerText = texts[mode] || '🔁';
        if (this.els.modeItems) {
            this.els.modeItems.forEach(item => {
                item.classList.toggle('active', Number(item.dataset.mode) === mode);
            });
        }
    },

    // ---------- 播放列表抽屉 ----------
    openPlaylist() {
        this.renderPlaylist();
        if (this.els.playlistPanel) this.els.playlistPanel.classList.add('open');
    },
    closePlaylist() {
        if (this.els.playlistPanel) this.els.playlistPanel.classList.remove('open');
    },
    renderPlaylist() {
        const body = this.els.playlistBody;
        const countEl = this.els.playlistCount;
        if (!body) return;

        const queue = AudioManager.songQueue || [];
        if (countEl) countEl.innerText = queue.length + ' 首';

        body.innerHTML = '';
        if (queue.length === 0) {
            body.innerHTML = '<div style="padding:30px;text-align:center;color:#94a3b8;">播放列表为空</div>';
            return;
        }
        queue.forEach((song, index) => {
            const item = document.createElement('div');
            item.className = 'playlist-item';
            if (index === AudioManager.currentIndex) item.classList.add('active');
            item.innerHTML = `
                <div class="pl-index">${index + 1}</div>
                <div class="pl-name">${escapeHtml(song.songName)}<span class="pl-author" style="color:#94a3b8;font-size:12px;margin-left:8px;">${escapeHtml(song.singer || '')}</span></div>
            `;
            item.onclick = () => {
                AudioManager.currentIndex = index;
                AudioManager.play(song);
                this.renderPlaylist();
            };
            body.appendChild(item);
        });
    },

    // ---------- 更新音量图标 ----------
    updateVolumeIcon(v) {
        if (!this.els.volumeIcon) return;
        if (v <= 0) this.els.volumeIcon.innerText = '🔇';
        else if (v < 0.5) this.els.volumeIcon.innerText = '🔉';
        else this.els.volumeIcon.innerText = '🔊';
    },

    // ---------- 更新收藏按钮 ----------
    updateCollectUI(collected) {
        if (!this.els.collectHeart) return;
        this.els.collectHeart.checked = !!collected;
    },

    // ---------- 异步查收藏状态 ----------
    async checkCollectStatus(songId) {
        try {
            const json = await request(`/api/collect/isCollect?songId=${songId}`);
            this.updateCollectUI(json.success && json.data);
        } catch (err) {
            console.warn('[PlayerUI] 查询收藏状态失败：', err);
        }
    },

    // ---------- 显示底部播放栏 ----------
    showBottomBar() {
        if (this.els.bottomBar){
            this.els.bottomBar.style.display = 'flex';
            document.body.classList.add('has-player');
        }
    }
};