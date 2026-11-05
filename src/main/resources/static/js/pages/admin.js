import { api } from '../api.js';
import {
    renderLayout, requireAuth, getMeta, h, clear, labelFor, formatPrice, formatDate, timeAgo, statusBadge,
    loadingState, emptyState, errorState, toast, withBusy, confirmDialog, showAlert, hideAlert,
} from '../ui.js';

const root = document.getElementById('admin-root');
const statsEl = document.getElementById('admin-stats');
const pendingBadge = document.getElementById('pending-badge');
const toolbar = document.getElementById('toolbar');
const content = document.getElementById('panel-content');
const panel = document.getElementById('panel');
const tabs = Array.from(document.querySelectorAll('.tab'));

const resolveDialog = document.getElementById('resolve-dialog');
const resolveForm = document.getElementById('resolve-form');
const resolveAlert = document.getElementById('resolve-alert');
let resolving = null;

let meta = null;
let currentTab = 'reports';

// ------------------------------------------------------------------ stats

async function loadStats() {
    try {
        const s = await api.adminStats();
        const stat = (value, label) => h('div', { class: 'card stat' },
            h('div', { class: 'stat-value', text: String(value) }), h('div', { class: 'stat-label', text: label }));
        clear(statsEl).append(
            stat(s.pendingReports, 'Pending reports'),
            stat(s.availableListings, 'Available listings'),
            stat(s.soldListings, 'Sold listings'),
            stat(s.hiddenListings, 'Hidden listings'),
            stat(s.totalUsers, 'Registered users'));
        pendingBadge.textContent = String(s.pendingReports);
        pendingBadge.classList.toggle('hidden', s.pendingReports === 0);
    } catch (err) {
        clear(statsEl).append(errorState(err, loadStats));
    }
}

// ------------------------------------------------------------------ reports

function reportStatusBadge(status) {
    if (status === 'PENDING') return h('span', { class: 'badge badge-warning', text: 'Pending' });
    if (status === 'ACTION_TAKEN') return h('span', { class: 'badge badge-success', text: 'Action taken' });
    return h('span', { class: 'badge', text: 'Dismissed' });
}

async function showReports(status = 'PENDING') {
    const select = h('select', { 'aria-label': 'Report status' },
        h('option', { value: 'PENDING', text: 'Pending', selected: status === 'PENDING' }),
        h('option', { value: 'ACTION_TAKEN', text: 'Action taken', selected: status === 'ACTION_TAKEN' }),
        h('option', { value: 'DISMISSED', text: 'Dismissed', selected: status === 'DISMISSED' }),
        h('option', { value: '', text: 'All reports', selected: status === '' }));
    select.addEventListener('change', () => showReports(select.value));
    clear(toolbar).append(select);
    clear(content).append(loadingState('Loading reports…'));
    try {
        const reports = await api.adminReports(status || undefined);
        if (reports.length === 0) {
            clear(content).append(emptyState('No reports here', status === 'PENDING' ? 'All caught up — nothing waiting for review.' : 'No reports match this filter.'));
            return;
        }
        const rows = reports.map((r) => {
            const actions = h('div', { class: 'btn-group' },
                h('a', { class: 'btn btn-secondary btn-sm', href: `/product.html?id=${encodeURIComponent(r.productId)}`, target: '_blank', rel: 'noopener', text: 'View listing' }));
            if (r.status === 'PENDING') {
                actions.append(h('button', { type: 'button', class: 'btn btn-primary btn-sm', text: 'Resolve', onclick: () => openResolve(r) }));
            }
            return h('tr', {},
                h('td', {},
                    h('div', { text: r.productTitle }),
                    h('div', { class: 'small muted' }, `Seller: ${r.sellerName} (${r.sellerEmail}) `, statusBadge(r.productStatus))),
                h('td', {},
                    h('div', { text: labelFor(meta.reportReasons, r.reason) }),
                    r.details ? h('div', { class: 'small muted pre-wrap', text: r.details }) : null,
                    h('div', { class: 'small muted', text: `By ${r.reporterName} · ${timeAgo(r.createdAt)}` })),
                h('td', {},
                    reportStatusBadge(r.status),
                    r.resolvedByName ? h('div', { class: 'small muted', text: `${r.resolvedByName}, ${formatDate(r.resolvedAt)}` }) : null,
                    r.adminNote ? h('div', { class: 'small muted pre-wrap', text: `Note: ${r.adminNote}` }) : null),
                h('td', {}, actions));
        });
        clear(content).append(h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
            h('thead', {}, h('tr', {}, ['Listing', 'Reason', 'Status', 'Actions'].map((t) => h('th', { scope: 'col', text: t })))),
            h('tbody', {}, rows))));
    } catch (err) {
        clear(content).append(errorState(err, () => showReports(status)));
    }
}

function openResolve(report) {
    resolving = report;
    resolveForm.reset();
    hideAlert(resolveAlert);
    document.getElementById('resolve-summary').textContent =
        `“${report.productTitle}” — ${labelFor(meta.reportReasons, report.reason)}`;
    resolveDialog.showModal();
}

resolveForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert(resolveAlert);
    const btn = document.getElementById('resolve-submit');
    await withBusy(btn, 'Saving…', async () => {
        try {
            await api.adminResolveReport(resolving.id, {
                status: resolveForm.elements.status.value,
                adminNote: resolveForm.elements.adminNote.value.trim(),
                hideListing: resolveForm.elements.hideListing.checked,
            });
            resolveDialog.close();
            toast('Report resolved', 'success');
            loadStats();
            showReports('PENDING');
        } catch (err) {
            showAlert(resolveAlert, err.message);
        }
    });
});
document.getElementById('resolve-cancel').addEventListener('click', () => resolveDialog.close());

// ------------------------------------------------------------------ listings

async function showListings(state = { q: '', status: '', page: 0 }) {
    const search = h('input', { type: 'search', placeholder: 'Search listings', 'aria-label': 'Search listings', value: state.q });
    const status = h('select', { 'aria-label': 'Listing status' },
        h('option', { value: '', text: 'All statuses', selected: state.status === '' }),
        h('option', { value: 'AVAILABLE', text: 'Available', selected: state.status === 'AVAILABLE' }),
        h('option', { value: 'SOLD', text: 'Sold', selected: state.status === 'SOLD' }),
        h('option', { value: 'HIDDEN', text: 'Hidden', selected: state.status === 'HIDDEN' }));
    const go = h('button', { type: 'submit', class: 'btn btn-primary', text: 'Search' });
    const form = h('form', { class: 'toolbar mb-0', role: 'search' }, search, status, go);
    form.addEventListener('submit', (e) => { e.preventDefault(); showListings({ q: search.value.trim(), status: status.value, page: 0 }); });
    status.addEventListener('change', () => showListings({ q: search.value.trim(), status: status.value, page: 0 }));
    clear(toolbar).append(form);

    clear(content).append(loadingState('Loading listings…'));
    try {
        const page = await api.adminProducts({ q: state.q, status: state.status, page: state.page, size: 20 });
        if (page.content.length === 0) {
            clear(content).append(emptyState('No listings found', 'Try a different search.'));
            return;
        }
        const rows = page.content.map((p) => {
            const actions = h('div', { class: 'btn-group' });
            const moderate = (newStatus, label, busy) => {
                const btn = h('button', { type: 'button', class: 'btn btn-secondary btn-sm', text: label });
                btn.addEventListener('click', () => withBusy(btn, busy, async () => {
                    try {
                        await api.adminSetProductStatus(p.id, newStatus);
                        toast(newStatus === 'HIDDEN' ? 'Listing hidden' : 'Listing restored', 'success');
                        loadStats();
                        showListings(state);
                    } catch (err) {
                        toast(err.message, 'error');
                    }
                }));
                return btn;
            };
            actions.append(p.status === 'HIDDEN' ? moderate('AVAILABLE', 'Restore', 'Restoring…') : moderate('HIDDEN', 'Hide', 'Hiding…'));
            const del = h('button', { type: 'button', class: 'btn btn-danger-outline btn-sm', text: 'Delete' });
            del.addEventListener('click', async () => {
                const ok = await confirmDialog({ title: 'Delete listing permanently?', message: `“${p.title}” and its photos and conversations will be removed.`, confirmLabel: 'Delete', danger: true });
                if (!ok) return;
                await withBusy(del, 'Deleting…', async () => {
                    try {
                        await api.adminDeleteProduct(p.id);
                        toast('Listing deleted', 'success');
                        loadStats();
                        showListings(state);
                    } catch (err) {
                        toast(err.message, 'error');
                    }
                });
            });
            actions.append(del);
            return h('tr', {},
                h('td', {}, h('a', { href: `/product.html?id=${encodeURIComponent(p.id)}`, text: p.title }),
                    h('div', { class: 'small muted', text: `${labelFor(meta.categories, p.category)} · ${p.campus}` })),
                h('td', { text: formatPrice(p.price) }),
                h('td', { text: p.sellerName }),
                h('td', {}, statusBadge(p.status)),
                h('td', {}, actions));
        });
        const pager = h('div', { class: 'pagination' });
        if (page.totalPages > 1) {
            pager.append(
                h('button', { type: 'button', class: 'btn btn-secondary btn-sm', disabled: page.page === 0, text: '← Previous', onclick: () => showListings({ ...state, page: page.page - 1 }) }),
                h('span', { class: 'small muted', text: `Page ${page.page + 1} of ${page.totalPages}` }),
                h('button', { type: 'button', class: 'btn btn-secondary btn-sm', disabled: page.page >= page.totalPages - 1, text: 'Next →', onclick: () => showListings({ ...state, page: page.page + 1 }) }));
        }
        clear(content).append(h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
            h('thead', {}, h('tr', {}, ['Listing', 'Price', 'Seller', 'Status', 'Actions'].map((t) => h('th', { scope: 'col', text: t })))),
            h('tbody', {}, rows))), pager);
    } catch (err) {
        clear(content).append(errorState(err, () => showListings(state)));
    }
}

// ------------------------------------------------------------------ users

async function showUsers(state = { q: '', page: 0 }) {
    const search = h('input', { type: 'search', placeholder: 'Search by name or email', 'aria-label': 'Search users', value: state.q });
    const form = h('form', { class: 'toolbar mb-0', role: 'search' }, search, h('button', { type: 'submit', class: 'btn btn-primary', text: 'Search' }));
    form.addEventListener('submit', (e) => { e.preventDefault(); showUsers({ q: search.value.trim(), page: 0 }); });
    clear(toolbar).append(form);

    clear(content).append(loadingState('Loading users…'));
    try {
        const page = await api.adminUsers({ q: state.q, page: state.page, size: 20 });
        if (page.content.length === 0) {
            clear(content).append(emptyState('No users found', 'Try a different search.'));
            return;
        }
        const rows = page.content.map((u) => {
            let action = h('span', { class: 'small muted', text: '—' });
            if (u.role !== 'ADMIN') {
                const btn = h('button', { type: 'button', class: `btn btn-sm ${u.enabled ? 'btn-danger-outline' : 'btn-secondary'}`, text: u.enabled ? 'Suspend' : 'Reinstate' });
                btn.addEventListener('click', async () => {
                    if (u.enabled) {
                        const ok = await confirmDialog({ title: `Suspend ${u.fullName}?`, message: 'They will be logged out of all actions and unable to log in until reinstated.', confirmLabel: 'Suspend', danger: true });
                        if (!ok) return;
                    }
                    await withBusy(btn, 'Saving…', async () => {
                        try {
                            await api.adminSetUserEnabled(u.id, !u.enabled);
                            toast(u.enabled ? 'User suspended' : 'User reinstated', 'success');
                            showUsers(state);
                        } catch (err) {
                            toast(err.message, 'error');
                        }
                    });
                });
                action = btn;
            }
            return h('tr', {},
                h('td', {}, h('div', { text: u.fullName }), h('div', { class: 'small muted', text: u.email })),
                h('td', { text: u.campus }),
                h('td', {}, h('span', { class: u.role === 'ADMIN' ? 'badge badge-primary' : 'badge', text: u.role })),
                h('td', { text: String(u.listingCount) }),
                h('td', {}, u.enabled ? h('span', { class: 'badge badge-success', text: 'Active' }) : h('span', { class: 'badge badge-danger', text: 'Suspended' })),
                h('td', { text: formatDate(u.createdAt) }),
                h('td', {}, action));
        });
        const pager = h('div', { class: 'pagination' });
        if (page.totalPages > 1) {
            pager.append(
                h('button', { type: 'button', class: 'btn btn-secondary btn-sm', disabled: page.page === 0, text: '← Previous', onclick: () => showUsers({ ...state, page: page.page - 1 }) }),
                h('span', { class: 'small muted', text: `Page ${page.page + 1} of ${page.totalPages}` }),
                h('button', { type: 'button', class: 'btn btn-secondary btn-sm', disabled: page.page >= page.totalPages - 1, text: 'Next →', onclick: () => showUsers({ ...state, page: page.page + 1 }) }));
        }
        clear(content).append(h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
            h('thead', {}, h('tr', {}, ['User', 'Campus', 'Role', 'Listings', 'Status', 'Joined', 'Actions'].map((t) => h('th', { scope: 'col', text: t })))),
            h('tbody', {}, rows))), pager);
    } catch (err) {
        clear(content).append(errorState(err, () => showUsers(state)));
    }
}

// ------------------------------------------------------------------ tabs

function selectTab(tab) {
    currentTab = tab;
    tabs.forEach((t) => {
        const selected = t.dataset.tab === tab;
        t.setAttribute('aria-selected', String(selected));
        t.tabIndex = selected ? 0 : -1;
        if (selected) panel.setAttribute('aria-labelledby', t.id);
    });
    if (tab === 'reports') showReports();
    else if (tab === 'listings') showListings();
    else showUsers();
}

tabs.forEach((t) => t.addEventListener('click', () => selectTab(t.dataset.tab)));

async function init() {
    await renderLayout();
    const user = await requireAuth({ admin: true });
    if (!user) return;
    if (user.forbidden) {
        clear(root).append(emptyState('Administrators only', 'Your account does not have access to the moderation dashboard.', { href: '/dashboard.html', label: 'Go to your dashboard' }));
        return;
    }
    try {
        meta = await getMeta();
    } catch (err) {
        clear(root).append(errorState(err, init));
        return;
    }
    loadStats();
    selectTab(currentTab);
}

init();
