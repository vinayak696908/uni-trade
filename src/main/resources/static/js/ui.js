// =====================================================================
// Shared UI helpers: layout (header/footer), DOM building, formatting,
// notifications, form error display and auth guards.
// SECURITY: user-generated text is only ever inserted with textContent
// (via h()), never with innerHTML.
// =====================================================================

import { api, ApiError } from './api.js';

// ------------------------------------------------------------------ DOM

/**
 * Create an element. attrs: class, text, html is NOT supported on purpose.
 * Keys starting with "on" attach listeners; boolean true sets an empty attribute.
 */
export function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
        if (value === undefined || value === null || value === false) continue;
        if (key === 'class') el.className = value;
        else if (key === 'text') el.textContent = value;
        else if (key === 'dataset') Object.assign(el.dataset, value);
        else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
        else if (value === true) el.setAttribute(key, '');
        else el.setAttribute(key, String(value));
    }
    appendChildren(el, children);
    return el;
}

function appendChildren(el, children) {
    for (const child of children.flat(Infinity)) {
        if (child === null || child === undefined || child === false) continue;
        el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
}

export function clear(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
    return el;
}

export function $(selector, root = document) {
    return root.querySelector(selector);
}

// ------------------------------------------------------------------ formatting

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0 });

export function formatPrice(value) {
    const n = Number(value);
    return Number.isFinite(n) ? inr.format(n) : '';
}

export function formatDate(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

export function timeAgo(iso) {
    if (!iso) return '';
    const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
    if (seconds < 60) return 'just now';
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.round(minutes / 60);
    if (hours < 24) return `${hours} h ago`;
    const days = Math.round(hours / 24);
    if (days < 30) return `${days} d ago`;
    return formatDate(iso);
}

export function initials(name) {
    return (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
}

// ------------------------------------------------------------------ reference data

let metaPromise = null;
export function getMeta() {
    if (!metaPromise) metaPromise = api.meta().catch((e) => { metaPromise = null; throw e; });
    return metaPromise;
}

export function labelFor(options, value) {
    const found = (options || []).find((o) => o.value === value);
    return found ? found.label : value;
}

export function fillSelect(select, options, { placeholder, selected } = {}) {
    clear(select);
    if (placeholder !== undefined) select.append(h('option', { value: '' , text: placeholder }));
    for (const opt of options) {
        const value = typeof opt === 'string' ? opt : opt.value;
        const label = typeof opt === 'string' ? opt : opt.label;
        select.append(h('option', { value, text: label, selected: value === selected }));
    }
}

const CATEGORY_IMAGES = {
    BOOKS: 'books', ELECTRONICS: 'electronics', CALCULATORS: 'calculators', LAB_EQUIPMENT: 'lab',
    STATIONERY: 'stationery', PROJECT_COMPONENTS: 'components', OTHER: 'other',
};

export function placeholderFor(category) {
    return `/images/categories/${CATEGORY_IMAGES[category] || 'other'}.svg`;
}

/** <img> for a listing: the uploaded thumbnail, or a category illustration. */
export function productImage(url, category, alt) {
    return url
        ? h('img', { src: url, alt: alt || '', loading: 'lazy' })
        : h('img', { src: placeholderFor(category), alt: alt || '', class: 'placeholder', loading: 'lazy' });
}

export function statusBadge(status) {
    if (status === 'SOLD') return h('span', { class: 'badge badge-warning', text: 'Sold' });
    if (status === 'HIDDEN') return h('span', { class: 'badge badge-danger', text: 'Hidden by moderator' });
    return h('span', { class: 'badge badge-success', text: 'Available' });
}

// ------------------------------------------------------------------ session & layout

let sessionPromise = null;
export function getSession() {
    if (!sessionPromise) {
        sessionPromise = api.session().catch(() => ({ authenticated: false, user: null }));
    }
    return sessionPromise;
}

const NAV_LINKS = [
    { href: '/marketplace.html', label: 'Marketplace' },
    { href: '/sell.html', label: 'Sell an item' },
];

function currentPath() {
    const p = window.location.pathname;
    return p === '/' ? '/index.html' : p;
}

function navLink(href, label) {
    return h('a', { href, text: label, 'aria-current': currentPath() === href ? 'page' : null });
}

export async function renderLayout() {
    const header = document.getElementById('site-header');
    const footer = document.getElementById('site-footer');
    const session = await getSession();
    const user = session.user;

    if (header) {
        const nav = h('nav', { class: 'nav', id: 'main-nav', 'aria-label': 'Main' },
            NAV_LINKS.map((l) => navLink(l.href, l.label)));
        if (user) {
            nav.append(
                navLink('/wishlist.html', 'Wishlist'),
                navLink('/inquiries.html', 'Inbox'),
                navLink('/dashboard.html', 'Dashboard'),
            );
            if (user.role === 'ADMIN') nav.append(navLink('/admin.html', 'Admin'));
            nav.append(h('button', { type: 'button', class: 'nav-link', onclick: logout, text: 'Log out' }));
        } else {
            nav.append(
                navLink('/login.html', 'Log in'),
                h('a', { href: '/register.html', class: 'btn btn-primary btn-sm', text: 'Sign up' }),
            );
        }
        const toggle = h('button', {
            type: 'button', class: 'nav-toggle', 'aria-controls': 'main-nav', 'aria-expanded': 'false',
            onclick: () => {
                const open = nav.classList.toggle('open');
                toggle.setAttribute('aria-expanded', String(open));
            },
        }, h('span', { class: 'sr-only', text: 'Menu' }),
            h('span', { class: 'nav-toggle-bar' }), h('span', { class: 'nav-toggle-bar' }), h('span', { class: 'nav-toggle-bar' }));

        clear(header).append(
            h('a', { class: 'skip-link', href: '#main', text: 'Skip to content' }),
            h('div', { class: 'container header-inner' },
                h('a', { class: 'brand', href: '/index.html' },
                    h('img', { src: '/images/logo.svg', alt: '' }),
                    h('div', {}, 'Uni', h('span', { text: 'Trade' }))),
                toggle,
                nav));
    }

    if (footer) {
        clear(footer).append(h('div', { class: 'container footer-inner' },
            h('div', {},
                h('strong', { text: 'UniTrade' }),
                h('div', { class: 'small muted', text: 'Second-hand books, gadgets and lab gear — from students, for students.' })),
            h('nav', { class: 'footer-links', 'aria-label': 'Footer' },
                h('a', { href: '/marketplace.html', text: 'Browse' }),
                h('a', { href: '/sell.html', text: 'Sell' }),
                h('a', { href: '/dashboard.html', text: 'My account' }),
                h('span', { class: 'small muted', text: `© ${new Date().getFullYear()} UniTrade` }))));
    }
    return session;
}

async function logout() {
    try {
        await api.logout();
        window.location.href = '/index.html';
    } catch (err) {
        toast(err.message, 'error');
    }
}

/** Ensures a logged-in user; otherwise redirects to login and returns null. */
export async function requireAuth({ admin = false } = {}) {
    const session = await getSession();
    if (!session.authenticated) {
        const next = window.location.pathname + window.location.search;
        window.location.replace(`/login.html?next=${encodeURIComponent(next)}`);
        return null;
    }
    if (admin && session.user.role !== 'ADMIN') {
        return { ...session.user, forbidden: true };
    }
    return session.user;
}

/** Only allow same-site relative redirects (prevents open-redirect via ?next=). */
export function safeNext(value, fallback = '/dashboard.html') {
    if (typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')) {
        return value;
    }
    return fallback;
}

export function param(name) {
    return new URLSearchParams(window.location.search).get(name);
}

// ------------------------------------------------------------------ feedback

let toastRegion = null;
export function toast(message, type = 'info') {
    if (!toastRegion) {
        toastRegion = h('div', { class: 'toast-region', role: 'status', 'aria-live': 'polite' });
        document.body.append(toastRegion);
    }
    const t = h('div', { class: `toast toast-${type}`, text: message });
    toastRegion.append(t);
    setTimeout(() => t.remove(), 4500);
}

export function alertBox(message, type = 'error') {
    return h('div', { class: `alert alert-${type}`, role: type === 'error' ? 'alert' : 'status' }, h('div', { text: message }));
}

/** Show a message inside a container (replacing previous content of that container). */
export function showAlert(container, message, type = 'error') {
    if (!container) return;
    clear(container).append(alertBox(message, type));
    container.classList.remove('hidden');
}

export function hideAlert(container) {
    if (!container) return;
    clear(container);
    container.classList.add('hidden');
}

export function loadingState(text = 'Loading…') {
    return h('div', { class: 'loading', role: 'status' }, h('span', { class: 'spinner', 'aria-hidden': 'true' }), h('span', { text }));
}

export function emptyState(title, message, action) {
    return h('div', { class: 'state' },
        h('h3', { text: title }),
        message ? h('p', { class: 'mb-0', text: message }) : null,
        action ? h('a', { class: 'btn btn-primary', href: action.href, text: action.label }) : null);
}

export function errorState(err, retry) {
    const box = h('div', { class: 'state' },
        h('h3', { text: err.status === 404 ? 'Not found' : 'Something went wrong' }),
        h('p', { class: 'mb-0', text: err.message || 'Please try again.' }));
    if (err.status === 401) {
        const next = window.location.pathname + window.location.search;
        box.append(h('a', { class: 'btn btn-primary', href: `/login.html?next=${encodeURIComponent(next)}`, text: 'Log in' }));
    } else if (retry) {
        box.append(h('button', { type: 'button', class: 'btn btn-secondary', onclick: retry, text: 'Try again' }));
    }
    return box;
}

/** Disable a button and show a spinner while an async action runs. */
export async function withBusy(button, busyText, fn) {
    const original = Array.from(button.childNodes);
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    clear(button).append(h('span', { class: 'spinner', 'aria-hidden': 'true' }), busyText);
    try {
        return await fn();
    } finally {
        button.disabled = false;
        button.removeAttribute('aria-busy');
        clear(button).append(...original);
    }
}

// ------------------------------------------------------------------ forms

export function clearFieldErrors(form) {
    form.querySelectorAll('[aria-invalid="true"]').forEach((el) => el.removeAttribute('aria-invalid'));
    form.querySelectorAll('.error[data-for]').forEach((el) => { el.textContent = ''; });
}

export function setFieldError(form, name, message) {
    const input = form.elements.namedItem(name);
    const slot = form.querySelector(`.error[data-for="${name}"]`);
    if (input && input.setAttribute) input.setAttribute('aria-invalid', 'true');
    if (slot) slot.textContent = message;
}

/** Map server-side validation errors (ApiError.fieldErrors) onto form fields. Returns true if any matched. */
export function applyFieldErrors(form, err) {
    let matched = false;
    if (err instanceof ApiError && err.fieldErrors) {
        for (const [field, message] of Object.entries(err.fieldErrors)) {
            if (form.elements.namedItem(field)) {
                setFieldError(form, field, message);
                matched = true;
            }
        }
    }
    const first = form.querySelector('[aria-invalid="true"]');
    if (first) first.focus();
    return matched;
}

export function formValues(form) {
    const data = {};
    new FormData(form).forEach((value, key) => {
        if (typeof value === 'string') data[key] = value.trim();
    });
    return data;
}

// ------------------------------------------------------------------ dialogs

/** Accessible confirm dialog. Resolves true/false. */
export function confirmDialog({ title, message, confirmLabel = 'Confirm', danger = false }) {
    return new Promise((resolve) => {
        const dialog = h('dialog', { 'aria-labelledby': 'confirm-title' },
            h('div', { class: 'dialog-body' },
                h('h3', { id: 'confirm-title', text: title }),
                h('p', { class: 'mb-0 muted', text: message })),
            h('div', { class: 'dialog-actions' },
                h('button', { type: 'button', class: 'btn btn-secondary', value: 'cancel', onclick: () => dialog.close('cancel'), text: 'Cancel' }),
                h('button', { type: 'button', class: `btn ${danger ? 'btn-danger' : 'btn-primary'}`, onclick: () => dialog.close('ok'), text: confirmLabel })));
        dialog.addEventListener('close', () => {
            resolve(dialog.returnValue === 'ok');
            dialog.remove();
        });
        document.body.append(dialog);
        dialog.showModal();
    });
}

// ------------------------------------------------------------------ product cards

/**
 * @param {object} p ProductSummaryDto
 * @param {{wishlisted?: boolean, onToggleWishlist?: Function, meta?: object, currentUserId?: number}} opts
 */
export function productCard(p, opts = {}) {
    const conditionLabel = opts.meta ? labelFor(opts.meta.conditions, p.condition) : p.condition;
    const card = h('article', { class: 'product-card' },
        h('a', { class: 'card-link', href: `/product.html?id=${encodeURIComponent(p.id)}` },
            h('div', { class: 'product-thumb' }, productImage(p.thumbnailUrl, p.category, p.title)),
            h('div', { class: 'product-info' },
                h('div', { class: 'product-price', text: formatPrice(p.price) }),
                h('h3', { class: 'product-title', text: p.title }),
                h('div', { class: 'product-meta', text: `${conditionLabel} · ${p.campus}` }),
                h('div', { class: 'product-meta', text: `${p.sellerName} · ${timeAgo(p.createdAt)}` }))));
    if (p.status && p.status !== 'AVAILABLE') {
        card.append(h('div', { class: 'status-flag' }, statusBadge(p.status)));
    }
    const ownListing = opts.currentUserId !== undefined && opts.currentUserId === p.sellerId;
    if (opts.onToggleWishlist && !ownListing) {
        const btn = h('button', {
            type: 'button', class: 'heart-btn',
            'aria-pressed': opts.wishlisted ? 'true' : 'false',
            'aria-label': opts.wishlisted ? `Remove ${p.title} from wishlist` : `Save ${p.title} to wishlist`,
            title: opts.wishlisted ? 'Remove from wishlist' : 'Save to wishlist',
        }, opts.wishlisted ? '♥' : '♡');
        btn.addEventListener('click', async (e) => {
            e.preventDefault();
            btn.disabled = true;
            try {
                const nowSaved = await opts.onToggleWishlist(p, btn.getAttribute('aria-pressed') === 'true');
                btn.setAttribute('aria-pressed', nowSaved ? 'true' : 'false');
                btn.setAttribute('aria-label', nowSaved ? `Remove ${p.title} from wishlist` : `Save ${p.title} to wishlist`);
                btn.textContent = nowSaved ? '♥' : '♡';
            } catch (err) {
                toast(err.message, 'error');
            } finally {
                btn.disabled = false;
            }
        });
        card.append(btn);
    }
    return card;
}

/** Shared wishlist toggle used by product grids. Returns the new saved state (only after the server confirms). */
export async function toggleWishlist(product, currentlySaved) {
    if (currentlySaved) {
        await api.removeFromWishlist(product.id);
        toast('Removed from your wishlist', 'success');
        return false;
    }
    await api.addToWishlist(product.id);
    toast('Saved to your wishlist', 'success');
    return true;
}
