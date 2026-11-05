import { api } from '../api.js';
import {
    renderLayout, requireAuth, h, clear, param, timeAgo, formatDateTime, placeholderFor, loadingState, emptyState,
    errorState, toast, withBusy, confirmDialog,
} from '../ui.js';

const listEl = document.getElementById('inbox-list');
const threadEl = document.getElementById('thread');
const panel = document.getElementById('inbox-panel');
const tabs = Array.from(document.querySelectorAll('.tab'));

let currentTab = param('tab') === 'sent' ? 'sent' : 'received';
let currentId = param('id');
let summaries = [];

function updateUrl() {
    const p = new URLSearchParams();
    p.set('tab', currentTab);
    if (currentId) p.set('id', currentId);
    history.replaceState(null, '', `?${p.toString()}`);
}

function selectTab(tab) {
    currentTab = tab;
    tabs.forEach((t) => {
        const selected = t.dataset.tab === tab;
        t.setAttribute('aria-selected', String(selected));
        t.tabIndex = selected ? 0 : -1;
        if (selected) panel.setAttribute('aria-labelledby', t.id);
    });
}

function thumb(url) {
    return url ? h('img', { src: url, alt: '' }) : h('img', { src: placeholderFor('OTHER'), alt: '', class: 'placeholder' });
}

function renderList() {
    if (summaries.length === 0) {
        clear(listEl).append(h('div', { class: 'card-body' }, currentTab === 'received'
            ? emptyState('No inquiries yet', 'When buyers message you about your listings, they appear here.')
            : emptyState('No conversations yet', 'Message a seller from any listing page.', { href: '/marketplace.html', label: 'Browse listings' })));
        clear(threadEl);
        return;
    }
    clear(listEl).append(...summaries.map((s) => h('button', {
        type: 'button', class: 'inbox-item', 'aria-current': String(String(s.id) === String(currentId)),
        onclick: () => openThread(s.id),
    },
    thumb(s.productThumbnailUrl),
    h('div', {},
        h('div', { class: 'name' }, h('span', { text: s.counterpartName }), h('span', { class: 'small muted', text: timeAgo(s.lastMessageAt) })),
        h('div', { class: 'small', text: s.productTitle }),
        h('div', { class: 'preview', text: s.status === 'CLOSED' ? `Closed · ${s.lastMessagePreview}` : s.lastMessagePreview })))));
}

async function loadList() {
    clear(listEl).append(loadingState());
    clear(threadEl).append(h('div', { class: 'card-body' }, emptyState('Select a conversation', 'Choose a conversation on the left to read and reply.')));
    try {
        summaries = currentTab === 'received' ? await api.receivedInquiries() : await api.sentInquiries();
        if (currentId && !summaries.some((s) => String(s.id) === String(currentId))) currentId = null;
        if (!currentId && summaries.length && window.matchMedia('(min-width: 861px)').matches) currentId = summaries[0].id;
        renderList();
        if (currentId) openThread(currentId);
        updateUrl();
    } catch (err) {
        clear(listEl).append(h('div', { class: 'card-body' }, errorState(err, loadList)));
    }
}

function renderThread(inq) {
    const counterpart = inq.myRole === 'BUYER' ? inq.sellerName : inq.buyerName;
    const messages = h('div', { class: 'messages' }, inq.messages.map((m) => h('div', { class: `message${m.mine ? ' mine' : ''}` },
        h('div', { class: 'pre-wrap', text: m.body }),
        h('div', { class: 'meta', text: `${m.mine ? 'You' : m.senderName} · ${formatDateTime(m.createdAt)}` }))));

    const head = h('div', { class: 'thread-head' },
        h('div', {},
            h('a', { href: `/product.html?id=${encodeURIComponent(inq.productId)}`, class: 'title', text: inq.productTitle }),
            h('div', { class: 'small muted', text: `${inq.myRole === 'BUYER' ? 'Seller' : 'Buyer'}: ${counterpart}` })));

    let footer;
    if (inq.status === 'OPEN') {
        const closeBtn = h('button', { type: 'button', class: 'btn btn-secondary btn-sm', text: 'Close conversation' });
        closeBtn.addEventListener('click', async () => {
            const ok = await confirmDialog({ title: 'Close this conversation?', message: 'Neither of you will be able to send more messages here.', confirmLabel: 'Close' });
            if (!ok) return;
            await withBusy(closeBtn, 'Closing…', async () => {
                try {
                    const updated = await api.closeInquiry(inq.id);
                    toast('Conversation closed', 'success');
                    renderThread(updated);
                    refreshSummary(updated);
                } catch (err) {
                    toast(err.message, 'error');
                }
            });
        });
        head.append(closeBtn);

        const textarea = h('textarea', { id: 'reply', name: 'message', required: true, maxlength: '2000', placeholder: `Reply to ${counterpart}…` });
        const send = h('button', { type: 'submit', class: 'btn btn-primary', text: 'Send' });
        footer = h('form', { class: 'reply-form', novalidate: true },
            h('label', { for: 'reply', class: 'sr-only', text: 'Reply' }), textarea, send);
        footer.addEventListener('submit', async (e) => {
            e.preventDefault();
            const message = textarea.value.trim();
            if (!message) {
                textarea.setAttribute('aria-invalid', 'true');
                textarea.focus();
                return;
            }
            textarea.removeAttribute('aria-invalid');
            await withBusy(send, 'Sending…', async () => {
                try {
                    const updated = await api.replyInquiry(inq.id, message);
                    renderThread(updated);
                    refreshSummary(updated);
                    const box = document.getElementById('reply');
                    if (box) box.focus();
                } catch (err) {
                    toast(err.message, 'error');
                }
            });
        });
    } else {
        head.append(h('span', { class: 'badge', text: 'Closed' }));
        footer = h('div', { class: 'reply-form small muted', text: 'This conversation is closed.' });
    }

    clear(threadEl).append(head, messages, footer);
    messages.scrollTop = messages.scrollHeight;
}

function refreshSummary(inq) {
    const s = summaries.find((x) => x.id === inq.id);
    if (s) {
        const last = inq.messages[inq.messages.length - 1];
        s.status = inq.status;
        s.lastMessagePreview = last ? last.body.slice(0, 120) : '';
        if (last) s.lastMessageAt = last.createdAt;
        renderList();
    }
}

async function openThread(id) {
    currentId = id;
    updateUrl();
    listEl.querySelectorAll('.inbox-item').forEach((el, i) => {
        el.setAttribute('aria-current', String(summaries[i] && String(summaries[i].id) === String(id)));
    });
    clear(threadEl).append(loadingState('Loading conversation…'));
    try {
        renderThread(await api.getInquiry(id));
    } catch (err) {
        clear(threadEl).append(h('div', { class: 'card-body' }, errorState(err, () => openThread(id))));
    }
}

tabs.forEach((t) => t.addEventListener('click', () => {
    if (t.dataset.tab === currentTab) return;
    selectTab(t.dataset.tab);
    currentId = null;
    loadList();
}));
tabs.forEach((t) => t.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
        const other = tabs.find((x) => x !== t);
        other.focus();
        other.click();
    }
}));

await renderLayout();
if (await requireAuth()) {
    selectTab(currentTab);
    loadList();
}
