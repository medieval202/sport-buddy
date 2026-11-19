const state = {
    token: localStorage.getItem("sportsBuddyToken"),
    user: null,
    activities: [],
    mine: [],
    venues: [],
    announcements: [],
    reservations: [],
    pending: [],
    users: [],
    radarSession: null,
    radarReceipt: null,
    socket: null,
    socketTimer: null,
    refreshTimer: null,
    heartbeatTimer: null,
    currentMatchActivityId: null,
    radar: {
        canvas: null,
        ctx: null,
        W: 560, H: 560, CX: 280, CY: 280, R: 240, dpr: 1,
        sweep: -Math.PI / 2,
        speed: 1.1,
        scanning: false,
        selected: null,
        hover: null,
        rafId: null,
        last: 0
    }
};

const viewMeta = {
    discover: ["DISCOVER", "发现运动", "找到与你合拍的运动搭子"],
    radar: ["RADAR MATCHING", "雷达匹配", "8 扇区轮询扫描、Top-N 结算与候选锁定"],
    mine: ["MY SCHEDULE", "我的组队", "管理我发起和参与的运动"],
    venues: ["VENUE BOOKING", "场地预约", "查看空闲时段并锁定运动场地"],
    profile: ["MY PROFILE", "个人档案", "完善数据，提升匹配准确度"],
    "admin-review": ["ACTIVITY REVIEW", "活动审核", "审核学生提交的公开活动"],
    "admin-users": ["USER MANAGEMENT", "用户管理", "管理学生信用分与账户状态"],
    "admin-announcements": ["ANNOUNCEMENTS", "公告发布", "向在线用户实时发布通知"]
};

const skillLabels = {
    BEGINNER: "新手",
    INTERMEDIATE: "进阶",
    ADVANCED: "高手"
};

const statusLabels = {
    PENDING: "待审核",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    CANCELLED: "已取消"
};

const slotLabels = {
    MORNING: "上午",
    AFTERNOON: "下午",
    EVENING: "晚上",
    WEEKEND: "周末"
};

document.addEventListener("DOMContentLoaded", boot);

async function boot() {
    bindEvents();
    setDefaultDates();
    if (state.token) {
        try {
            state.user = await api("/api/me");
            await openApp();
            return;
        } catch (error) {
            clearSession(false);
        }
    }
    showAuth();
}

function bindEvents() {
    document.querySelectorAll("[data-auth-tab]").forEach(button => {
        button.addEventListener("click", () => switchAuthTab(button.dataset.authTab));
    });

    document.getElementById("login-form").addEventListener("submit", login);
    document.getElementById("register-form").addEventListener("submit", register);
    document.getElementById("logout-btn").addEventListener("click", () => logout());

    document.querySelectorAll("[data-demo-user]").forEach(button => {
        button.addEventListener("click", () => {
            document.getElementById("login-username").value = button.dataset.demoUser;
            document.getElementById("login-password").value = button.dataset.demoPassword;
        });
    });

    document.querySelectorAll("[data-view]").forEach(button => {
        button.addEventListener("click", () => showView(button.dataset.view));
    });

    document.addEventListener("click", handleActionClick);
    document.querySelectorAll(".modal-backdrop").forEach(backdrop => {
        backdrop.addEventListener("click", event => {
            if (event.target === backdrop) {
                backdrop.classList.add("hidden");
            }
        });
    });

    document.getElementById("activity-form").addEventListener("submit", createActivity);
    document.getElementById("radar-form").addEventListener("submit", startRadarSession);
    document.getElementById("reservation-form").addEventListener("submit", createReservation);
    document.getElementById("profile-form").addEventListener("submit", saveProfile);
    document.getElementById("announcement-form").addEventListener("submit", publishAnnouncement);
    document.getElementById("reservation-venue").addEventListener("change", loadBusySlots);
    document.getElementById("reservation-date").addEventListener("change", loadBusySlots);

    // 雷达控制按钮
    document.getElementById("btnScan").addEventListener("click", async () => {
        if (state.radarSession) {
            // 切换本地扫描动画暂停/继续
            state.radar.scanning = !state.radar.scanning;
            document.getElementById("btnScan").textContent = state.radar.scanning ? "⏸ 暂停扫描" : "▶ 继续扫描";
            logRadar("雷达", state.radar.scanning ? "扫描恢复" : "扫描已暂停", "warn");
        } else {
            document.getElementById("radar-form").requestSubmit();
        }
    });
    document.getElementById("btnClose").addEventListener("click", async () => {
        if (state.radarSession) {
            await api(`/api/radar/sessions/${state.radarSession.sessionId}`, { method: "DELETE" });
            state.radarSession = null;
            state.radar.scanning = false;
            document.getElementById("btnScan").textContent = "▶ 启动扫描";
            renderRadar();
            toast("雷达会话已关闭");
        }
    });
    document.getElementById("radar-radius-range").addEventListener("input", e => {
        document.getElementById("radar-radius").value = e.target.value;
        document.getElementById("lbRadius").textContent = e.target.value + " km";
    });
    document.getElementById("rSpeed").addEventListener("input", e => {
        state.radar.speed = +e.target.value;
        document.getElementById("lbSpeed").textContent = (+e.target.value).toFixed(1) + "×";
    });
    document.getElementById("r-mask").addEventListener("click", e => {
        if (e.target.id === "r-mask") {
            document.getElementById("r-mask").classList.remove("show");
        }
    });

    document.getElementById("sport-filter").addEventListener("input", event => {
        const keyword = event.target.value.trim().toLowerCase();
        const filtered = state.activities.filter(activity =>
            !keyword
            || activity.sportType.toLowerCase().includes(keyword)
            || activity.title.toLowerCase().includes(keyword)
        );
        renderActivities(filtered, "activity-grid");
    });
    document.getElementById("refresh-activities").addEventListener("click", async () => {
        await loadActivities();
        renderActivities(state.activities, "activity-grid");
        toast("活动列表已刷新");
    });
}

async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) {
        headers.set("Authorization", `Bearer ${state.token}`);
    }
    if (options.body && !(options.body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(path, { ...options, headers });
    if (response.status === 401 && state.token) {
        clearSession(true);
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (error) {
            data = text;
        }
    }

    if (!response.ok) {
        const message = data && data.message
            ? data.message
            : `请求失败（${response.status}）`;
        const error = new Error(message);
        error.status = response.status;
        throw error;
    }
    return data;
}

function showAuth() {
    document.getElementById("auth-screen").classList.remove("hidden");
    document.getElementById("app-shell").classList.add("hidden");
}

async function openApp() {
    document.getElementById("auth-screen").classList.add("hidden");
    document.getElementById("app-shell").classList.remove("hidden");
    applyUserChrome();
    connectSocket();
    initRadarCanvas();
    await refreshAll();
}

function applyUserChrome() {
    if (!state.user) {
        return;
    }
    const isAdmin = state.user.role === "ADMIN";
    document.querySelectorAll(".admin-only").forEach(element => {
        element.classList.toggle("hidden", !isAdmin);
    });
    document.querySelectorAll(".student-only").forEach(element => {
        element.classList.toggle("hidden", isAdmin);
    });
    document.getElementById("side-user-name").textContent = state.user.displayName;
    document.getElementById("side-user-role").textContent = isAdmin ? "系统管理员" : "学生用户";
    document.getElementById("side-avatar").textContent = isAdmin ? "管" : state.user.displayName.slice(0, 1);
    document.getElementById("top-credit").textContent = state.user.creditScore;
    document.getElementById("profile-credit").textContent = state.user.creditScore;
}

async function refreshAll() {
    const requests = [
        api("/api/activities"),
        api("/api/activities/mine"),
        api("/api/venues"),
        api("/api/announcements"),
        api("/api/reservations/mine")
    ];
    if (state.user.role === "ADMIN") {
        requests.push(api("/api/admin/activities/pending"));
        requests.push(api("/api/admin/users"));
    }

    try {
        const results = await Promise.all(requests);
        state.activities = results[0];
        state.mine = results[1];
        state.venues = results[2];
        state.announcements = results[3];
        state.reservations = results[4];
        if (state.user.role === "ADMIN") {
            state.pending = results[5];
            state.users = results[6];
        }
        renderAll();
    } catch (error) {
        toast(error.message, true);
    }
}

function renderAll() {
    renderActivities(state.activities, "activity-grid");
    renderActivities(state.mine, "mine-grid");
    renderAnnouncements();
    renderVenues();
    renderReservations();
    renderProfile();
    renderPending();
    renderUsers();
    renderAdminAnnouncements();
    document.getElementById("pending-badge").textContent = state.pending.length;
}

function renderActivities(activities, containerId) {
    const container = document.getElementById(containerId);
    if (!activities.length) {
        container.innerHTML = `
            <div class="empty-state">
                暂无运动活动。点击“发起运动”，提交你的第一场组队吧。
            </div>`;
        return;
    }

    container.innerHTML = activities.map(activity => {
        const ratio = activity.maxParticipants
            ? Math.min(100, activity.participantCount / activity.maxParticipants * 100)
            : 0;
        let statusChip = "";
        if (activity.status === "PENDING") {
            statusChip = `<span class="status-chip pending">待审核</span>`;
        } else if (activity.status === "REJECTED") {
            statusChip = `<span class="status-chip rejected">已驳回</span>`;
        } else if (activity.joined) {
            statusChip = `<span class="status-chip joined">已加入</span>`;
        }

        const isFull = activity.participantCount >= activity.maxParticipants;
        let primaryAction = "";
        if (activity.status === "APPROVED") {
            primaryAction = activity.owned
                ? `<button class="btn btn-ghost" disabled>我发起的</button>`
                : activity.joined
                ? `<button class="btn btn-ghost" data-action="leave" data-id="${activity.id}">退出</button>`
                : `<button class="btn btn-primary" data-action="join" data-id="${activity.id}" ${isFull ? "disabled" : ""}>
                    ${isFull ? "人数已满" : "加入活动"}
                   </button>`;
        } else if (activity.status === "PENDING") {
            primaryAction = `<button class="btn btn-ghost" disabled>等待审核</button>`;
        } else {
            primaryAction = `<button class="btn btn-ghost" disabled>不可加入</button>`;
        }

        const matchButton = activity.status === "APPROVED"
            ? `<button class="btn btn-outline" data-action="match" data-id="${activity.id}">AI 匹配</button>`
            : "";

        return `
            <article class="activity-card">
                <div class="card-top">
                    <span class="sport-chip">${escapeHtml(activity.sportType)}</span>
                    ${statusChip}
                </div>
                <h3>${escapeHtml(activity.title)}</h3>
                <p>${escapeHtml(activity.description || "发起人暂未填写活动说明。")}</p>
                ${activity.status === "REJECTED" && activity.rejectReason
                    ? `<div class="announcement-card"><p>驳回原因：${escapeHtml(activity.rejectReason)}</p></div>`
                    : ""}
                <div class="meta-list">
                    <span>时间 <strong>${formatDateTime(activity.startTime)}</strong></span>
                    <span>地点 <strong>${escapeHtml(activity.locationName)}</strong></span>
                    <span>发起 <strong>${escapeHtml(activity.creatorName)}</strong></span>
                    <span>门槛 <strong>${skillLabels[activity.requiredSkillLevel]} · 信用 ${activity.minCreditScore}+</strong></span>
                </div>
                <div class="progress-line"><i style="width:${ratio}%"></i></div>
                <div class="meta-list" style="border:0;padding:0;margin:0 0 13px">
                    <span>组队进度 <strong>${activity.participantCount}/${activity.maxParticipants} 人</strong></span>
                </div>
                <div class="card-actions">
                    ${matchButton}
                    ${primaryAction}
                </div>
            </article>`;
    }).join("");
}

function renderAnnouncements() {
    const container = document.getElementById("announcement-list");
    if (!state.announcements.length) {
        container.innerHTML = `<div class="empty-state">暂无校园公告</div>`;
        return;
    }
    container.innerHTML = state.announcements.slice(0, 4).map(item => `
        <article class="announcement-card">
            <strong>${escapeHtml(item.title)}</strong>
            <p>${escapeHtml(item.content)}</p>
        </article>
    `).join("");

    document.getElementById("admin-announcement-list").innerHTML =
        state.announcements.map(item => `
            <article class="admin-announcement-item">
                <strong>${escapeHtml(item.title)}</strong>
                <p>${escapeHtml(item.content)}</p>
                <small>${escapeHtml(item.authorName)} · ${formatDateTime(item.createdAt)}</small>
            </article>
        `).join("") || `<div class="empty-state">暂无公告</div>`;
}

function renderAdminAnnouncements() {
    const container = document.getElementById("admin-announcement-list");
    container.innerHTML = state.announcements.length
        ? state.announcements.map(item => `
            <article class="admin-announcement-item">
                <strong>${escapeHtml(item.title)}</strong>
                <p>${escapeHtml(item.content)}</p>
                <small>${escapeHtml(item.authorName)} · ${formatDateTime(item.createdAt)}</small>
            </article>
        `).join("")
        : `<div class="empty-state">暂无公告</div>`;
}

function renderVenues() {
    const select = document.getElementById("reservation-venue");
    const previous = select.value;
    select.innerHTML = state.venues.map(venue =>
        `<option value="${venue.id}">${escapeHtml(venue.name)} · ${escapeHtml(venue.campusZone)}</option>`
    ).join("");
    if (previous && state.venues.some(venue => String(venue.id) === previous)) {
        select.value = previous;
    }

    document.getElementById("venue-list").innerHTML = state.venues.length
        ? state.venues.map(venue => `
            <article class="venue-item">
                <strong>${escapeHtml(venue.name)}</strong>
                <p>${escapeHtml(venue.address)}</p>
                <small>${venue.openTime.slice(0, 5)} - ${venue.closeTime.slice(0, 5)} · ${venue.indoor ? "室内" : "室外"}</small>
            </article>
        `).join("")
        : `<div class="empty-state">暂无可用场地</div>`;

    loadBusySlots();
}

function renderReservations() {
    const container = document.getElementById("reservation-list");
    if (!state.reservations.length) {
        container.innerHTML = `<div class="empty-state">还没有场地预约</div>`;
        return;
    }
    container.innerHTML = state.reservations.map(reservation => `
        <article class="reservation-item ${reservation.status === "CANCELLED" ? "cancelled" : ""}">
            <div>
                <strong>${escapeHtml(reservation.venueName)}</strong>
                <p>${reservation.reservationDate} · ${reservation.startTime.slice(0, 5)} - ${reservation.endTime.slice(0, 5)}</p>
                <p>${reservation.status === "CONFIRMED" ? "已确认" : statusLabels[reservation.status]}</p>
            </div>
            ${reservation.status === "CONFIRMED"
                ? `<button class="btn btn-danger btn-small" data-action="cancel-reservation" data-id="${reservation.id}">取消</button>`
                : ""}
        </article>
    `).join("");
}

function renderProfile() {
    if (!state.user) {
        return;
    }
    document.getElementById("profile-name").value = state.user.displayName;
    document.getElementById("profile-username").value = state.user.username;
    document.getElementById("profile-skill").value = state.user.skillLevel;
    document.getElementById("profile-zone").value = state.user.campusZone;
    document.getElementById("profile-sports").value = state.user.sportPreferences.join(",");
    document.getElementById("profile-slots").value = state.user.availableTimeSlots.join(",");
    document.getElementById("profile-latitude").value = state.user.latitude;
    document.getElementById("profile-longitude").value = state.user.longitude;
    renderRadarProfile();
}

function renderRadarProfile() {
    if (!state.user) {
        return;
    }
    document.getElementById("radar-profile-sports").innerHTML = state.user.sportPreferences
        .map(sport => `<span>${escapeHtml(sport)}</span>`).join("");
    document.getElementById("radar-profile-skill").textContent = skillLabels[state.user.skillLevel] || state.user.skillLevel;
    document.getElementById("radar-profile-times").innerHTML = state.user.availableTimeSlots
        .map(slot => `<span>${escapeHtml(slotLabels[slot] || slot)}</span>`).join("");
    document.getElementById("radar-profile-zone").textContent = state.user.campusZone;
    document.getElementById("radar-profile-location").textContent =
        `纬度 ${Number(state.user.latitude).toFixed(4)} / 经度 ${Number(state.user.longitude).toFixed(4)}`;
}

function renderPending() {
    const container = document.getElementById("pending-list");
    if (!container) {
        return;
    }
    if (!state.pending.length) {
        container.innerHTML = `<div class="empty-state">当前没有待审核活动</div>`;
        return;
    }
    container.innerHTML = state.pending.map(activity => `
        <article class="review-card">
            <div class="card-top">
                <span class="sport-chip">${escapeHtml(activity.sportType)}</span>
                <span class="status-chip pending">待审核</span>
            </div>
            <h3>${escapeHtml(activity.title)}</h3>
            <p>${escapeHtml(activity.description || "无活动说明")}</p>
            <div class="review-meta">
                <span>${formatDateTime(activity.startTime)}</span>
                <span>${escapeHtml(activity.locationName)}</span>
                <span>${escapeHtml(activity.creatorName)}</span>
                <span>${activity.maxParticipants} 人</span>
                <span>信用 ${activity.minCreditScore}+</span>
            </div>
            <div class="card-actions">
                <button class="btn btn-danger btn-small" data-action="reject-activity" data-id="${activity.id}">驳回</button>
                <button class="btn btn-primary btn-small" data-action="approve-activity" data-id="${activity.id}">通过审核</button>
            </div>
        </article>
    `).join("");
}

function renderUsers() {
    const tbody = document.getElementById("user-table");
    if (!tbody) {
        return;
    }
    tbody.innerHTML = state.users.map(user => `
        <tr>
            <td><strong>${escapeHtml(user.displayName)}</strong><small>@${escapeHtml(user.username)} · ${escapeHtml(user.campusZone)}</small></td>
            <td>${user.sportPreferences.map(escapeHtml).join(" / ")}</td>
            <td>${skillLabels[user.skillLevel]}</td>
            <td><span class="credit-value">${user.creditScore}</span></td>
            <td><span class="account-status ${user.enabled ? "" : "disabled"}">${user.enabled ? "正常" : "已禁用"}</span></td>
            <td>
                <div class="table-actions">
                    <button class="btn btn-outline btn-small" data-action="adjust-credit" data-id="${user.id}">调整分</button>
                    <button class="btn ${user.enabled ? "btn-danger" : "btn-primary"} btn-small" data-action="toggle-user" data-id="${user.id}" data-enabled="${user.enabled}">
                        ${user.enabled ? "禁用" : "启用"}
                    </button>
                </div>
            </td>
        </tr>
    `).join("") || `<tr><td colspan="6">暂无学生用户</td></tr>`;
}

async function handleActionClick(event) {
    const button = event.target.closest("[data-action]");
    if (!button) {
        return;
    }
    const action = button.dataset.action;
    const id = Number(button.dataset.id);

    try {
        if (action === "open-create") {
            openModal("activity-modal");
        } else if (action === "close-modal") {
            button.closest(".modal-backdrop").classList.add("hidden");
        } else if (action === "join") {
            await api(`/api/activities/${id}/join`, { method: "POST" });
            toast("已成功加入活动");
            await refreshActivityData();
        } else if (action === "leave") {
            if (!window.confirm("确定退出该活动吗？")) {
                return;
            }
            await api(`/api/activities/${id}/join`, { method: "DELETE" });
            toast("已退出活动");
            await refreshActivityData();
        } else if (action === "match") {
            await openMatches(id);
        } else if (action === "invite") {
            const userId = Number(button.dataset.userId);
            await api(`/api/activities/${state.currentMatchActivityId}/invite/${userId}`, { method: "POST" });
            toast("邀请通知已发送");
        } else if (action === "approve-activity") {
            await api(`/api/admin/activities/${id}/review`, {
                method: "PATCH",
                body: JSON.stringify({ approved: true })
            });
            toast("活动已通过审核");
            await refreshAdminData();
            await refreshActivityData();
        } else if (action === "reject-activity") {
            const reason = window.prompt("请输入驳回原因：");
            if (!reason) {
                return;
            }
            await api(`/api/admin/activities/${id}/review`, {
                method: "PATCH",
                body: JSON.stringify({ approved: false, reason })
            });
            toast("活动已驳回");
            await refreshAdminData();
        } else if (action === "adjust-credit") {
            const user = state.users.find(item => item.id === id);
            const value = window.prompt(`输入 ${user.displayName} 的新信用分（0-100）：`, user.creditScore);
            if (value === null) {
                return;
            }
            await api(`/api/admin/users/${id}`, {
                method: "PATCH",
                body: JSON.stringify({ creditScore: Number(value) })
            });
            toast("信用分已更新");
            await refreshAdminData();
        } else if (action === "toggle-user") {
            const enabled = button.dataset.enabled === "true";
            await api(`/api/admin/users/${id}`, {
                method: "PATCH",
                body: JSON.stringify({ enabled: !enabled })
            });
            toast(enabled ? "账户已禁用" : "账户已启用");
            await refreshAdminData();
        } else if (action === "cancel-reservation") {
            if (!window.confirm("确定取消该场地预约吗？")) {
                return;
            }
            await api(`/api/reservations/${id}`, { method: "DELETE" });
            toast("预约已取消");
            state.reservations = await api("/api/reservations/mine");
            renderReservations();
            await loadBusySlots();
        } else if (action === "radar-lock") {
            const candidateId = Number(button.dataset.userId);
            if (!state.radarSession) {
                return;
            }
            state.radarSession = await api(
                `/api/radar/sessions/${state.radarSession.sessionId}/candidates/${candidateId}/lock`,
                { method: "POST" }
            );
            state.radar.scanning = false;
            document.getElementById("btnScan").textContent = "▶ 启动扫描";
            renderRadar();
            logRadar("锁定", `已锁定候选，雷达扫描暂停`, "warn");
            toast("候选已锁定，雷达扫描暂停");
        } else if (action === "radar-engage") {
            if (!state.radarSession || !state.radarSession.selectedCandidateId) {
                return;
            }
            const candidateId = state.radarSession.selectedCandidateId;
            const result = await api(
                `/api/radar/sessions/${state.radarSession.sessionId}/candidates/${candidateId}/engage`,
                { method: "POST" }
            );
            state.radarReceipt = result;
            state.radarSession = null;
            state.radar.scanning = false;
            document.getElementById("btnScan").textContent = "▶ 启动扫描";
            renderRadar();
            logRadar("邀约", `组队邀约已发送：${result.message}`, "hi");
            toast(result.message);
        } else if (action === "radar-resume") {
            if (!state.radarSession) {
                return;
            }
            state.radarSession = await api(
                `/api/radar/sessions/${state.radarSession.sessionId}/resume`,
                { method: "POST" }
            );
            state.radar.scanning = true;
            document.getElementById("btnScan").textContent = "⏸ 暂停扫描";
            renderRadar();
            logRadar("雷达", "扫描已恢复", "warn");
            toast("雷达扫描已恢复");
        } else if (action === "radar-simulate") {
            if (!state.radarSession) {
                return;
            }
            state.radarSession = await api(
                `/api/radar/sessions/${state.radarSession.sessionId}/simulate-event`,
                { method: "POST" }
            );
            renderRadar();
            logRadar("模拟", state.radarSession.lastEvent || "候选事件已模拟", "warn");
            toast(state.radarSession.lastEvent);
        } else if (action === "radar-close") {
            if (!state.radarSession) {
                return;
            }
            await api(`/api/radar/sessions/${state.radarSession.sessionId}`, { method: "DELETE" });
            state.radarSession = null;
            state.radar.scanning = false;
            document.getElementById("btnScan").textContent = "▶ 启动扫描";
            renderRadar();
            logRadar("雷达", "会话已关闭", "warn");
            toast("雷达会话已关闭");
        }
    } catch (error) {
        toast(error.message, true);
    }
}

async function startRadarSession(event) {
    event.preventDefault();
    state.radarReceipt = null;
    const payload = {
        radiusKm: Number(document.getElementById("radar-radius").value),
        topN: Number(document.getElementById("radar-top-n").value),
        simulationMode: document.getElementById("radar-simulation").checked
    };
    try {
        state.radarSession = await api("/api/radar/sessions", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        state.radar.scanning = true;
        document.getElementById("btnScan").textContent = "⏸ 暂停扫描";
        startRadarLoop();
        renderRadar();
        logRadar("系统", "雷达已启动，正在扫描 8 个扇区…", "hi");
        toast("雷达已启动，正在扫描 8 个扇区");
    } catch (error) {
        toast(error.message, true);
    }
}

async function loadCurrentRadar() {
    try {
        state.radarSession = await api("/api/radar/sessions/current");
        renderRadar();
    } catch (error) {
        if (error.status !== 404) {
            toast(error.message, true);
        }
    }
}

function renderRadar() {
    const stScan = document.getElementById("stScan");
    const stBest = document.getElementById("stBest");
    const stSector = document.getElementById("stSector");
    const radarTag = document.getElementById("radarTag");
    const stateChip = document.getElementById("radar-state-chip");
    const actionContainer = document.getElementById("radar-actions");
    const list = document.getElementById("radar-candidate-list");

    const session = state.radarSession;
    if (!session) {
        stScan.textContent = "0";
        stBest.textContent = "--";
        stSector.textContent = "0/8";
        radarTag.textContent = "IDLE";
        stateChip.textContent = state.radarReceipt ? state.radarReceipt.status : "IDLE";
        stateChip.className = "r-tag";
        actionContainer.innerHTML = "";
        list.innerHTML = state.radarReceipt
            ? `<div class="r-empty">已向 ${escapeHtml(state.radarReceipt.candidateName)} 发起邀约，匹配分 ${state.radarReceipt.matchScore}，等待对方确认。</div>`
            : `<div class="r-empty">📡 启动雷达后展示 Top-N 候选<br>光束扫过目标后自动入榜</div>`;
        return;
    }

    const running = session.state === "RUNNING";
    const blips = session.blips || [];
    const best = blips.length ? Math.max(...blips.map(b => b.score)) : 0;

    stScan.textContent = `${blips.length}`;
    stBest.textContent = blips.length ? best.toFixed(0) : "--";
    stSector.textContent = `${session.currentSector + 1}/${session.sectors}`;
    radarTag.textContent = running ? "SWEEPING…" : session.state;
    stateChip.textContent = session.state;
    stateChip.className = `r-tag ${session.state.toLowerCase()}`;

    // 操作按钮
    const selected = blips.find(b => b.userId === session.selectedCandidateId);
    const actionButtons = [];
    if (running) {
        actionButtons.push(`<button class="btn btn-outline btn-small" data-action="radar-simulate">模拟候选下线</button>`);
    }
    if (session.state === "PAUSED" && selected) {
        actionButtons.push(`<button class="btn btn-primary btn-small" data-action="radar-engage">向 ${escapeHtml(selected.displayName)} 发起组队</button>`);
        actionButtons.push(`<button class="btn btn-ghost btn-small" data-action="radar-resume">继续扫描</button>`);
    }
    actionContainer.innerHTML = actionButtons.join("");

    // 候选榜单
    renderRadarRank(blips, session);
}

function renderRadarRank(blips, session) {
    const list = document.getElementById("radar-candidate-list");
    if (!blips.length) {
        list.innerHTML = `<div class="r-empty">当前半径内没有符合条件的候选，调整距离或运动项目后重试。</div>`;
        return;
    }
    list.innerHTML = blips.map((blip, index) => {
        const selectedClass = blip.userId === session.selectedCandidateId ? " sel" : "";
        const c = tierColor(blip.score);
        const action = blip.userId === session.selectedCandidateId && session.state === "PAUSED"
            ? `<button class="r-invite" data-action="radar-engage">发起组队</button>`
            : `<button class="r-invite" data-action="radar-lock" data-user-id="${blip.userId}">锁定</button>`;
        const breakdown = blip.scoreBreakdown || {};
        const dims = ["sport", "time", "distance", "skill", "credit"];
        return `
            <div class="r-card${index < 3 ? " top" + (index + 1) : ""}${selectedClass}" data-user-id="${blip.userId}">
                <div class="r-rk">${index + 1}</div>
                <div class="r-av">${escapeHtml(blip.displayName.slice(0, 1))}${blip.online ? '<i class="r-on"></i>' : ""}</div>
                <div class="r-cinfo">
                    <div class="r-cname">${escapeHtml(blip.displayName)}<span class="r-lv">${skillLabels[blip.skillLevel]}</span></div>
                    <div class="r-cmeta">${escapeHtml(blip.campusZone)} · ${blip.distanceKm}km · 信用 ${blip.creditScore}</div>
                    <div class="r-ctags">
                        ${(blip.highlights || []).slice(0, 3).map(h => `<span class="r-ctag r-hit">${escapeHtml(h)}</span>`).join("")}
                    </div>
                    <div class="r-bars">${dims.map(d => `<i style="height:${Math.max(8, (breakdown[d] || 0))}%"></i>`).join("")}</div>
                </div>
                <div class="r-cscore">
                    <div class="r-snum" style="color:${c}">${blip.score}<small>分</small></div>
                    ${action}
                </div>
            </div>`;
    }).join("");

    // 点击卡片查看详情
    list.querySelectorAll(".r-card").forEach(card => {
        card.addEventListener("click", ev => {
            if (ev.target.closest("[data-action]")) return;
            const id = Number(card.dataset.userId);
            openRadarModal(id);
        });
    });
}

function tierColor(score) {
    return score >= 85 ? "var(--r-cy)" : score >= 70 ? "var(--r-bl)" : score >= 55 ? "var(--r-am)" : "var(--r-rd)";
}

function tierName(score) {
    return score >= 85 ? "灵魂搭子" : score >= 70 ? "高度契合" : score >= 55 ? "可以一试" : "匹配偏低";
}

/* ==================== Canvas 雷达绘制 ==================== */
function initRadarCanvas() {
    const canvas = document.getElementById("radar-canvas");
    if (!canvas) return;
    const r = state.radar;
    r.canvas = canvas;
    r.ctx = canvas.getContext("2d");
    resizeRadar();
    new ResizeObserver(resizeRadar).observe(canvas.parentElement);
    bindRadarCanvasEvents();
}

function resizeRadar() {
    const r = state.radar;
    if (!r.canvas) return;
    const w = r.canvas.parentElement.clientWidth - 20;
    r.W = r.H = Math.max(300, Math.min(w, 600));
    r.dpr = Math.min(window.devicePixelRatio || 1, 2);
    r.canvas.width = r.W * r.dpr;
    r.canvas.height = r.H * r.dpr;
    r.canvas.style.height = r.H + "px";
    r.ctx.setTransform(r.dpr, 0, 0, r.dpr, 0, 0);
    r.CX = r.W / 2;
    r.CY = r.H / 2;
    r.R = r.W / 2 - 34;
}

function blipRadius(score) {
    return Math.max(10, state.radar.R * (1 - score / 100));
}

function blipPosition(blip) {
    const r = state.radar;
    const angle = (blip.angle || 0) * Math.PI / 180;
    const radius = blipRadius(blip.score || 0);
    return [r.CX + Math.cos(angle) * radius, r.CY + Math.sin(angle) * radius];
}

function drawRadarGrid() {
    const r = state.radar;
    const ctx = r.ctx;
    ctx.save();
    const g = ctx.createRadialGradient(r.CX, r.CY, 10, r.CX, r.CY, r.R);
    g.addColorStop(0, "rgba(46, 230, 197, .10)");
    g.addColorStop(.6, "rgba(20, 45, 80, .16)");
    g.addColorStop(1, "rgba(8, 16, 30, .05)");
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(r.CX, r.CY, r.R, 0, 7);
    ctx.fill();

    ctx.strokeStyle = "rgba(90, 150, 210, .20)";
    ctx.lineWidth = 1;
    [20, 40, 60, 80, 100].forEach(s => {
        ctx.beginPath();
        ctx.arc(r.CX, r.CY, blipRadius(s), 0, 7);
        ctx.stroke();
        ctx.fillStyle = "rgba(130, 165, 200, .5)";
        ctx.font = "9.5px monospace";
        ctx.textAlign = "left";
        ctx.fillText(s + "", r.CX + 4, r.CY - blipRadius(s) - 3);
    });

    ctx.strokeStyle = "rgba(90, 150, 210, .13)";
    for (let i = 0; i < 12; i++) {
        const a = i * Math.PI / 6;
        ctx.beginPath();
        ctx.moveTo(r.CX, r.CY);
        ctx.lineTo(r.CX + Math.cos(a) * r.R, r.CY + Math.sin(a) * r.R);
        ctx.stroke();
    }

    ctx.strokeStyle = "rgba(46, 230, 197, .35)";
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.arc(r.CX, r.CY, r.R, 0, 7);
    ctx.stroke();

    ctx.fillStyle = "rgba(130, 165, 200, .55)";
    ctx.font = "10px monospace";
    ctx.textAlign = "center";
    ctx.fillText("匹配度 100 → 圆心", r.CX, r.CY + r.R + 20);
    ctx.restore();
}

function drawRadarSweep() {
    const r = state.radar;
    const ctx = r.ctx;
    const span = 1.05, steps = 46;
    ctx.save();
    for (let i = 0; i < steps; i++) {
        const a0 = r.sweep - span * (i + 1) / steps;
        const a1 = r.sweep - span * i / steps;
        const al = .20 * Math.pow(1 - i / steps, 2.1);
        ctx.beginPath();
        ctx.moveTo(r.CX, r.CY);
        ctx.arc(r.CX, r.CY, r.R, a0, a1);
        ctx.closePath();
        ctx.fillStyle = "rgba(46, 230, 197, " + al + ")";
        ctx.fill();
    }
    const grd = ctx.createLinearGradient(r.CX, r.CY, r.CX + Math.cos(r.sweep) * r.R, r.CY + Math.sin(r.sweep) * r.R);
    grd.addColorStop(0, "rgba(46, 230, 197, .15)");
    grd.addColorStop(1, "rgba(46, 230, 197, .95)");
    ctx.strokeStyle = grd;
    ctx.lineWidth = 2;
    ctx.shadowBlur = 16;
    ctx.shadowColor = "rgba(46, 230, 197, .8)";
    ctx.beginPath();
    ctx.moveTo(r.CX, r.CY);
    ctx.lineTo(r.CX + Math.cos(r.sweep) * r.R, r.CY + Math.sin(r.sweep) * r.R);
    ctx.stroke();
    ctx.restore();
}

function drawRadarMe(t) {
    const r = state.radar;
    const ctx = r.ctx;
    ctx.save();
    const p = (t / 900) % 1;
    ctx.strokeStyle = "rgba(46, 230, 197, " + (.45 * (1 - p)) + ")";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(r.CX, r.CY, 10 + p * 26, 0, 7);
    ctx.stroke();
    ctx.fillStyle = "#2ee6c5";
    ctx.shadowBlur = 18;
    ctx.shadowColor = "#2ee6c5";
    ctx.beginPath();
    ctx.arc(r.CX, r.CY, 7, 0, 7);
    ctx.fill();
    ctx.shadowBlur = 0;
    ctx.fillStyle = "#04121c";
    ctx.font = "bold 8px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("ME", r.CX, r.CY + .5);
    ctx.restore();
}

function drawRadarBlips(t) {
    const r = state.radar;
    const ctx = r.ctx;
    const session = state.radarSession;
    if (!session) return;
    const blips = session.blips || [];
    blips.forEach(blip => {
        const [x, y] = blipPosition(blip);
        const c = tierColor(blip.score);
        ctx.save();
        ctx.globalAlpha = .95;
        ctx.fillStyle = c;
        ctx.shadowBlur = 10;
        ctx.shadowColor = c;
        ctx.beginPath();
        ctx.arc(x, y, 3.4, 0, 7);
        ctx.fill();
        if (!blip.online) {
            ctx.globalAlpha = .6;
            ctx.strokeStyle = "#7f93ad";
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.arc(x, y, 6, 0, 7);
            ctx.stroke();
        }
        ctx.restore();

        if (r.selected === blip.userId || r.hover === blip.userId) {
            ctx.save();
            ctx.strokeStyle = c;
            ctx.lineWidth = 1.2;
            ctx.setLineDash([4, 4]);
            ctx.beginPath();
            ctx.moveTo(r.CX, r.CY);
            ctx.lineTo(x, y);
            ctx.stroke();
            ctx.setLineDash([]);
            ctx.beginPath();
            ctx.arc(x, y, 10, 0, 7);
            ctx.stroke();
            ctx.fillStyle = c;
            ctx.font = "bold 11px sans-serif";
            ctx.textAlign = "center";
            ctx.fillText(blip.displayName + " · " + blip.score, x, y - 16);
            ctx.restore();
        }
    });
}

function startRadarLoop() {
    const r = state.radar;
    if (r.rafId) return;
    r.last = performance.now();
    r.rafId = requestAnimationFrame(radarLoop);
}

function radarLoop(t) {
    const r = state.radar;
    const dt = Math.min(.05, (t - r.last) / 1000);
    r.last = t;
    if (r.scanning) {
        r.sweep += r.speed * dt * 1.6;
        if (r.sweep > Math.PI * 1.5) {
            r.sweep -= Math.PI * 2;
        }
    }
    if (r.ctx) {
        r.ctx.clearRect(0, 0, r.W, r.H);
        drawRadarGrid();
        if (r.scanning) drawRadarSweep();
        drawRadarBlips(t);
        drawRadarMe(t);
    }
    r.rafId = requestAnimationFrame(radarLoop);
}

function bindRadarCanvasEvents() {
    const r = state.radar;
    const canvas = r.canvas;
    if (!canvas) return;
    canvas.addEventListener("mousemove", e => {
        const session = state.radarSession;
        if (!session) return;
        const rect = canvas.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;
        let best = null, bd = 14;
        (session.blips || []).forEach(blip => {
            const [x, y] = blipPosition(blip);
            const d = Math.hypot(x - mx, y - my);
            if (d < bd) { bd = d; best = blip; }
        });
        r.hover = best ? best.userId : null;
        const tip = document.getElementById("r-tip");
        if (best) {
            tip.style.opacity = 1;
            tip.style.left = Math.min(mx + 14, r.W - 190) + "px";
            tip.style.top = (my - 10) + "px";
            tip.innerHTML = `<b>${escapeHtml(best.displayName)}</b> · ${best.score}分
                <div class="r-tt-row">${skillLabels[best.skillLevel]} · ${escapeHtml(best.campusZone)} · ${best.distanceKm}km</div>
                <div class="r-tt-row">${tierName(best.score)}</div>`;
            canvas.style.cursor = "pointer";
        } else {
            tip.style.opacity = 0;
            canvas.style.cursor = "crosshair";
        }
    });
    canvas.addEventListener("mouseleave", () => {
        document.getElementById("r-tip").style.opacity = 0;
        r.hover = null;
    });
    canvas.addEventListener("click", () => {
        if (r.hover) openRadarModal(r.hover);
    });
}

function openRadarModal(userId) {
    const session = state.radarSession;
    if (!session) return;
    const blip = (session.blips || []).find(b => b.userId === userId);
    if (!blip) return;
    state.radar.selected = userId;
    const c = tierColor(blip.score);
    const breakdown = blip.scoreBreakdown || {};
    const dims = [
        { k: "sport", n: "运动项目", v: breakdown.sport || 0 },
        { k: "time", n: "时间窗", v: breakdown.time || 0 },
        { k: "distance", n: "距离", v: breakdown.distance || 0 },
        { k: "skill", n: "水平", v: breakdown.skill || 0 },
        { k: "credit", n: "信用", v: breakdown.credit || 0 }
    ];
    const sportMatched = (blip.highlights || []).includes("项目匹配");
    const timeMatched = (blip.highlights || []).includes("时间匹配");
    const rows = [
        ["运动项目", state.user.sportPreferences.join("、"), sportMatched ? "项目有重合" : "项目相似度较低", sportMatched],
        ["技术水平", skillLabels[state.user.skillLevel], skillLabels[blip.skillLevel], state.user.skillLevel === blip.skillLevel],
        ["可约时段", state.user.availableTimeSlots.map(slot => slotLabels[slot] || slot).join("、"), timeMatched ? "时段有重合" : "时段相似度较低", timeMatched],
        ["距离范围", "≤" + session.radiusKm + "km", blip.distanceKm + "km", blip.distanceKm <= session.radiusKm],
        ["信用分", "—", blip.creditScore, blip.creditScore >= 60]
    ];
    const modal = document.getElementById("r-modal");
    modal.innerHTML = `
        <div class="r-mhead">
            <div class="r-av">${escapeHtml(blip.displayName.slice(0, 1))}${blip.online ? '<i class="r-on"></i>' : ''}</div>
            <div>
                <h3>${escapeHtml(blip.displayName)} <span style="font-size:12px;color:${c}">· ${tierName(blip.score)}</span></h3>
                <p>${skillLabels[blip.skillLevel]} · ${escapeHtml(blip.campusZone)} · 信用 ${blip.creditScore} · ${blip.online ? '当前在线' : '离线'}</p>
            </div>
            <button class="r-mclose" id="r-mx">✕</button>
        </div>
        <div class="r-mbody">
            <div>
                <canvas id="r-mini-radar" width="260" height="260" style="width:100%;max-width:260px;display:block;margin:0 auto"></canvas>
                <div class="r-mscore"><b style="color:${c}">${blip.score}</b><i>综合匹配度 / 100 · 五维加权相似度</i></div>
            </div>
            <div>
                ${dims.map(d => `<div class="r-dimrow"><span>${d.n}</span><div class="r-dimbar"><i style="width:${d.v}%"></i></div><b>${d.v}</b></div>`).join("")}
                <table class="r-cmp">${rows.map(r2 => `<tr><td>${r2[0]}</td><td>${escapeHtml(String(r2[1]))}</td><td class="${r2[3] ? 'r-same' : ''}">${escapeHtml(String(r2[2]))}</td></tr>`).join("")}</table>
                <div class="r-reason"><b>🤖 智能匹配解读：</b>该候选匹配度 <b>${blip.score}</b> 分，位于「${escapeHtml(blip.campusZone)}」，距离 ${blip.distanceKm}km。${(blip.highlights || []).length ? '匹配亮点：' + blip.highlights.map(escapeHtml).join("、") : ''}</div>
            </div>
        </div>
        <div class="r-mfoot">
            <button class="r-btn r-btn-primary" id="r-mInv">🤝 锁定并发起组队</button>
            <button class="r-btn" id="r-mClose">关闭</button>
        </div>`;
    document.getElementById("r-mask").classList.add("show");
    drawMiniRadar(blip, dims);
    document.getElementById("r-mx").onclick = () => document.getElementById("r-mask").classList.remove("show");
    document.getElementById("r-mClose").onclick = () => document.getElementById("r-mask").classList.remove("show");
    document.getElementById("r-mInv").onclick = async () => {
        try {
            state.radarSession = await api(
                `/api/radar/sessions/${state.radarSession.sessionId}/candidates/${blip.userId}/lock`,
                { method: "POST" }
            );
            renderRadar();
            document.getElementById("r-mask").classList.remove("show");
            toast("候选已锁定，可发起组队");
        } catch (err) {
            toast(err.message, true);
        }
    };
}

function drawMiniRadar(blip, dims) {
    const c = document.getElementById("r-mini-radar");
    if (!c) return;
    const x = c.getContext("2d");
    const s = 260, cx = 130, cy = 132, rad = 88, n = dims.length;
    x.clearRect(0, 0, s, s);
    for (let k = 1; k <= 4; k++) {
        x.beginPath();
        for (let i = 0; i < n; i++) {
            const a = -Math.PI / 2 + i * 2 * Math.PI / n;
            const rr = rad * k / 4;
            const px = cx + Math.cos(a) * rr;
            const py = cy + Math.sin(a) * rr;
            i ? x.lineTo(px, py) : x.moveTo(px, py);
        }
        x.closePath();
        x.strokeStyle = "rgba(90, 150, 210, " + (k === 4 ? .35 : .15) + ")";
        x.stroke();
    }
    x.strokeStyle = "rgba(90, 150, 210, .18)";
    for (let i = 0; i < n; i++) {
        const a = -Math.PI / 2 + i * 2 * Math.PI / n;
        x.beginPath();
        x.moveTo(cx, cy);
        x.lineTo(cx + Math.cos(a) * rad, cy + Math.sin(a) * rad);
        x.stroke();
    }
    x.beginPath();
    dims.forEach((d, i) => {
        const a = -Math.PI / 2 + i * 2 * Math.PI / n;
        const rr = rad * (d.v / 100);
        const px = cx + Math.cos(a) * rr;
        const py = cy + Math.sin(a) * rr;
        i ? x.lineTo(px, py) : x.moveTo(px, py);
    });
    x.closePath();
    const g = x.createLinearGradient(0, 40, 0, 220);
    g.addColorStop(0, "rgba(46, 230, 197, .45)");
    g.addColorStop(1, "rgba(76, 157, 255, .28)");
    x.fillStyle = g;
    x.fill();
    x.strokeStyle = "#2ee6c5";
    x.lineWidth = 2;
    x.stroke();
    dims.forEach((d, i) => {
        const a = -Math.PI / 2 + i * 2 * Math.PI / n;
        const rr = rad * (d.v / 100);
        x.fillStyle = "#2ee6c5";
        x.beginPath();
        x.arc(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr, 3, 0, 7);
        x.fill();
        x.fillStyle = "#9fb3c9";
        x.font = "11px sans-serif";
        x.textAlign = "center";
        x.textBaseline = "middle";
        x.fillText(d.n, cx + Math.cos(a) * (rad + 20), cy + Math.sin(a) * (rad + 18));
    });
}

function logRadar(tag, msg, cls) {
    const logEl = document.getElementById("r-log");
    if (!logEl) return;
    const d = document.createElement("div");
    if (cls) d.className = cls;
    const t = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    d.innerHTML = `[${t}] <b style="color:#5f7d99">${tag}</b> › ${msg}`;
    logEl.prepend(d);
    while (logEl.children.length > 40) logEl.lastChild.remove();
}

function handleRadarEvent(payload) {
    if (payload.type === "RADAR_INVITATION" || payload.type === "RADAR_MATCH_ACCEPTED" || payload.type === "RADAR_MATCH_REJECTED") {
        toast(payload.message);
        return;
    }
    const session = payload.data;
    if (!session || !session.sessionId) {
        return;
    }
    if (payload.type === "RADAR_CLOSED") {
        state.radarSession = null;
        state.radar.scanning = false;
        document.getElementById("btnScan").textContent = "▶ 启动扫描";
        renderRadar();
        toast(payload.message);
        return;
    }
    if (!state.radarSession || state.radarSession.sessionId === session.sessionId) {
        state.radarSession = session;
        if (session.state === "RUNNING") {
            state.radar.scanning = true;
            document.getElementById("btnScan").textContent = "⏸ 暂停扫描";
            startRadarLoop();
        } else {
            state.radar.scanning = false;
            document.getElementById("btnScan").textContent = "▶ 启动扫描";
        }
        renderRadar();
        logRadar("事件", payload.message || "雷达状态已更新", payload.type === "RADAR_EVENT" ? "hi" : "");
    }
}

async function login(event) {
    event.preventDefault();
    const username = document.getElementById("login-username").value.trim();
    const password = document.getElementById("login-password").value;
    try {
        const response = await api("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password })
        });
        state.token = response.token;
        state.user = response.user;
        localStorage.setItem("sportsBuddyToken", state.token);
        toast(`欢迎回来，${response.user.displayName}`);
        await openApp();
        showView(response.user.role === "ADMIN" ? "admin-review" : "discover");
    } catch (error) {
        toast(error.message, true);
    }
}

async function register(event) {
    event.preventDefault();
    const payload = {
        username: document.getElementById("reg-username").value.trim(),
        password: document.getElementById("reg-password").value,
        displayName: document.getElementById("reg-display-name").value.trim(),
        sportPreferences: splitValues(document.getElementById("reg-sports").value),
        skillLevel: document.getElementById("reg-skill").value,
        campusZone: document.getElementById("reg-zone").value.trim(),
        availableTimeSlots: splitValues(document.getElementById("reg-slots").value),
        latitude: 31.2304,
        longitude: 121.4737
    };
    try {
        const response = await api("/api/auth/register", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        state.token = response.token;
        state.user = response.user;
        localStorage.setItem("sportsBuddyToken", state.token);
        toast("注册成功，开始寻找运动搭子吧");
        await openApp();
        showView("discover");
    } catch (error) {
        toast(error.message, true);
    }
}

async function createActivity(event) {
    event.preventDefault();
    const payload = {
        title: document.getElementById("activity-title").value.trim(),
        sportType: document.getElementById("activity-sport").value.trim(),
        description: document.getElementById("activity-description").value.trim(),
        campusZone: document.getElementById("activity-zone").value.trim(),
        locationName: document.getElementById("activity-location").value.trim(),
        startTime: document.getElementById("activity-start").value,
        endTime: document.getElementById("activity-end").value,
        maxParticipants: Number(document.getElementById("activity-max").value),
        requiredSkillLevel: document.getElementById("activity-skill").value,
        minCreditScore: Number(document.getElementById("activity-credit").value),
        latitude: Number(document.getElementById("activity-latitude").value),
        longitude: Number(document.getElementById("activity-longitude").value)
    };
    try {
        await api("/api/activities", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        document.getElementById("activity-modal").classList.add("hidden");
        document.getElementById("activity-form").reset();
        setDefaultDates();
        toast("活动已提交，等待管理员审核");
        await refreshActivityData();
        showView("mine");
    } catch (error) {
        toast(error.message, true);
    }
}

async function openMatches(activityId) {
    state.currentMatchActivityId = activityId;
    const activity = [...state.activities, ...state.mine].find(item => item.id === activityId);
    document.getElementById("match-title").textContent = activity
        ? `${activity.title} · 推荐搭子`
        : "智能搭子推荐";
    document.getElementById("match-list").innerHTML = `<div class="empty-state">正在计算匹配分...</div>`;
    openModal("match-modal");

    try {
        const matches = await api(`/api/activities/${activityId}/matches?limit=10`);
        const container = document.getElementById("match-list");
        container.innerHTML = matches.length
            ? matches.map((match, index) => `
                <article class="match-item">
                    <span class="match-rank">${index + 1}</span>
                    <div class="match-person">
                        <strong>${escapeHtml(match.displayName)}
                            <small>${skillLabels[match.skillLevel]} · ${escapeHtml(match.campusZone)} · ${match.distanceKm}km</small>
                        </strong>
                        <p>${match.highlights.map(escapeHtml).join(" · ") || "可进一步了解"}</p>
                        <div class="match-bar"><i style="width:${match.matchScore}%"></i></div>
                    </div>
                    <div class="match-score">
                        <strong>${match.matchScore}</strong><small> 匹配分</small>
                    </div>
                    <button class="btn btn-outline btn-small" data-action="invite" data-user-id="${match.userId}">邀请</button>
                </article>
            `).join("")
            : `<div class="empty-state">暂未找到符合条件的候选人</div>`;
    } catch (error) {
        document.getElementById("match-list").innerHTML =
            `<div class="empty-state">${escapeHtml(error.message)}</div>`;
    }
}

async function createReservation(event) {
    event.preventDefault();
    const payload = {
        venueId: Number(document.getElementById("reservation-venue").value),
        reservationDate: document.getElementById("reservation-date").value,
        startTime: document.getElementById("reservation-start").value,
        endTime: document.getElementById("reservation-end").value
    };
    try {
        await api("/api/reservations", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        toast("场地预约成功");
        state.reservations = await api("/api/reservations/mine");
        renderReservations();
        await loadBusySlots();
    } catch (error) {
        toast(error.message, true);
    }
}

async function loadBusySlots() {
    const venueId = document.getElementById("reservation-venue").value;
    const date = document.getElementById("reservation-date").value;
    const container = document.getElementById("busy-slots");
    if (!venueId || !date) {
        container.innerHTML = "<span>选择场地和日期后查看</span>";
        return;
    }
    try {
        const slots = await api(`/api/venues/${venueId}/busy?date=${encodeURIComponent(date)}`);
        container.innerHTML = slots.length
            ? slots.map(slot => `<span class="busy">${slot.startTime.slice(0, 5)} - ${slot.endTime.slice(0, 5)} 已占用</span>`).join("")
            : "<span>当前日期暂无占用，可预约</span>";
    } catch (error) {
        container.innerHTML = `<span>${escapeHtml(error.message)}</span>`;
    }
}

async function saveProfile(event) {
    event.preventDefault();
    const payload = {
        displayName: document.getElementById("profile-name").value.trim(),
        sportPreferences: splitValues(document.getElementById("profile-sports").value),
        skillLevel: document.getElementById("profile-skill").value,
        campusZone: document.getElementById("profile-zone").value.trim(),
        availableTimeSlots: splitValues(document.getElementById("profile-slots").value),
        latitude: Number(document.getElementById("profile-latitude").value),
        longitude: Number(document.getElementById("profile-longitude").value)
    };
    try {
        state.user = await api("/api/me", {
            method: "PUT",
            body: JSON.stringify(payload)
        });
        applyUserChrome();
        renderProfile();
        toast("个人档案已保存，匹配结果将随之更新");
    } catch (error) {
        toast(error.message, true);
    }
}

async function publishAnnouncement(event) {
    event.preventDefault();
    const payload = {
        title: document.getElementById("announcement-title").value.trim(),
        content: document.getElementById("announcement-content").value.trim()
    };
    try {
        await api("/api/admin/announcements", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        event.target.reset();
        state.announcements = await api("/api/announcements");
        renderAnnouncements();
        renderAdminAnnouncements();
        toast("公告已发布并实时推送");
    } catch (error) {
        toast(error.message, true);
    }
}

function connectSocket() {
    if (state.socket) {
        state.socket.onclose = null;
        state.socket.close();
    }
    if (!state.token) {
        return;
    }
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(
        `${protocol}//${window.location.host}/ws/notifications?token=${encodeURIComponent(state.token)}`
    );
    state.socket = socket;

    socket.onopen = () => {
        clearInterval(state.heartbeatTimer);
        state.heartbeatTimer = setInterval(() => {
            if (state.socket?.readyState === WebSocket.OPEN) {
                state.socket.send(JSON.stringify({ type: "PING" }));
            }
        }, 25000);
    };

    socket.onmessage = event => {
        try {
            const payload = JSON.parse(event.data);
            if (payload.type === "PONG") {
                return;
            }
            if (payload.type.startsWith("RADAR_")) {
                handleRadarEvent(payload);
                return;
            }
            toast(payload.message || "收到一条新通知");
            if (payload.type !== "ACTIVITY_INVITATION") {
                scheduleRefresh();
            }
        } catch (error) {
            console.warn("无法解析 WebSocket 通知", error);
        }
    };

    socket.onclose = () => {
        clearInterval(state.heartbeatTimer);
        if (!state.token) {
            return;
        }
        clearTimeout(state.socketTimer);
        state.socketTimer = setTimeout(connectSocket, 3000);
    };
}

function scheduleRefresh() {
    clearTimeout(state.refreshTimer);
    state.refreshTimer = setTimeout(() => refreshAll(), 500);
}

async function refreshActivityData() {
    const [activities, mine] = await Promise.all([
        api("/api/activities"),
        api("/api/activities/mine")
    ]);
    state.activities = activities;
    state.mine = mine;
    renderActivities(state.activities, "activity-grid");
    renderActivities(state.mine, "mine-grid");
}

async function refreshAdminData() {
    const [pending, users] = await Promise.all([
        api("/api/admin/activities/pending"),
        api("/api/admin/users")
    ]);
    state.pending = pending;
    state.users = users;
    renderPending();
    renderUsers();
    document.getElementById("pending-badge").textContent = pending.length;
}

async function loadActivities() {
    state.activities = await api("/api/activities");
}

function showView(view) {
    if (view.startsWith("admin-") && state.user.role !== "ADMIN") {
        return;
    }
    document.querySelectorAll(".view").forEach(section => section.classList.remove("active"));
    document.querySelectorAll(".nav-item").forEach(button => {
        button.classList.toggle("active", button.dataset.view === view);
    });
    document.getElementById(`view-${view}`).classList.add("active");
    const [eyebrow, title, subtitle] = viewMeta[view];
    document.getElementById("view-eyebrow").textContent = eyebrow;
    document.getElementById("view-title").textContent = title;
    document.getElementById("view-subtitle").textContent = subtitle;
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (view === "venues") {
        loadBusySlots();
    } else if (view === "radar") {
        loadCurrentRadar();
        resizeRadar();
        startRadarLoop();
    }
}

function switchAuthTab(tab) {
    document.querySelectorAll("[data-auth-tab]").forEach(button => {
        button.classList.toggle("active", button.dataset.authTab === tab);
    });
    document.getElementById("login-form").classList.toggle("hidden", tab !== "login");
    document.getElementById("register-form").classList.toggle("hidden", tab !== "register");
}

function openModal(id) {
    document.getElementById(id).classList.remove("hidden");
}

function logout() {
    clearSession(true);
    showAuth();
    switchAuthTab("login");
}

function clearSession(notify) {
    if (state.socket) {
        state.socket.onclose = null;
        state.socket.close();
    }
    state.token = null;
    state.user = null;
    state.socket = null;
    localStorage.removeItem("sportsBuddyToken");
    clearTimeout(state.socketTimer);
    clearTimeout(state.refreshTimer);
    clearInterval(state.heartbeatTimer);
    if (notify) {
        toast("登录状态已失效，请重新登录", true);
    }
}

function toast(message, isError = false) {
    const stack = document.getElementById("toast-stack");
    const element = document.createElement("div");
    element.className = `toast ${isError ? "error" : ""}`;
    element.innerHTML = `<span class="toast-dot"></span><span>${escapeHtml(message)}</span>`;
    stack.appendChild(element);
    setTimeout(() => {
        element.style.opacity = "0";
        element.style.transform = "translateX(14px)";
        setTimeout(() => element.remove(), 250);
    }, 4200);
}

function setDefaultDates() {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    document.getElementById("reservation-date").value = localDate(tomorrow);

    const start = new Date(tomorrow);
    start.setHours(19, 30, 0, 0);
    const end = new Date(start);
    end.setHours(20, 30, 0, 0);
    document.getElementById("activity-start").value = localDateTime(start);
    document.getElementById("activity-end").value = localDateTime(end);
}

function localDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function localDateTime(date) {
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");
    return `${localDate(date)}T${hour}:${minute}`;
}

function formatDateTime(value) {
    if (!value) {
        return "";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        month: "numeric",
        day: "numeric",
        weekday: "short",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

function splitValues(value) {
    return value.split(/[,，]/).map(item => item.trim()).filter(Boolean);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
