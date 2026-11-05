import { api } from '../api.js';
import {
    renderLayout, getMeta, h, clear, param, formatPrice, formatDate, labelFor, statusBadge, productImage,
    placeholderFor, initials, loadingState, errorState, alertBox, toast, withBusy, confirmDialog, fillSelect,
    clearFieldErrors, applyFieldErrors, showAlert, hideAlert,
} from '../ui.js';

const root = document.getElementById('product-root');
const crumb = document.getElementById('crumb-title');
const reportDialog = document.getElementById('report-dialog');
const reportForm = document.getElementById('report-form');
const reportAlert = document.getElementById('report-alert');

const productId = param('id');
let session = { authenticated: false };
let meta = null;
let product = null;

function loginLink(text) {
    const next = window.location.pathname + window.location.search;
    return h('a', { class: 'btn btn-primary btn-block', href: `/login.html?next=${encodeURIComponent(next)}`, text });
}

function gallery(p) {
    const main = h('div', { class: 'gallery-main' });
    const setMain = (img) => {
        clear(main).append(img
            ? h('img', { src: img.url, alt: p.title })
            : h('img', { src: placeholderFor(p.category), alt: p.title, class: 'placeholder' }));
    };
    setMain(p.images[0]);
    const thumbs = h('div', { class: 'gallery-thumbs' });
    if (p.images.length > 1) {
        p.images.forEach((img, index) => {
            const btn = h('button', {
                type: 'button', 'aria-label': `Show photo ${index + 1}`, 'aria-current': index === 0 ? 'true' : 'false',
                onclick: () => {
                    setMain(img);
                    thumbs.querySelectorAll('button').forEach((b) => b.setAttribute('aria-current', 'false'));
                    btn.setAttribute('aria-current', 'true');
                },
            }, h('img', { src: img.url, alt: '' }));
            thumbs.append(btn);
        });
    }
    return h('div', {}, main, thumbs);
}

function ownerActions(p) {
    const box = h('div', { class: 'detail-section' }, h('h3', { text: 'Manage your listing' }));
    if (p.status === 'HIDDEN') {
        box.append(alertBox('A moderator has hidden this listing from the marketplace. You can still edit or delete it.', 'warning'));
    }
    const group = h('div', { class: 'btn-group mt-1' },
        h('a', { class: 'btn btn-secondary', href: `/sell.html?id=${encodeURIComponent(p.id)}`, text: 'Edit listing' }));
    if (p.status !== 'HIDDEN') {
        const toSold = p.status === 'AVAILABLE';
        const statusBtn = h('button', { type: 'button', class: 'btn btn-secondary', text: toSold ? 'Mark as sold' : 'Mark as available' });
        statusBtn.addEventListener('click', () => withBusy(statusBtn, 'Saving…', async () => {
            try {
                product = await api.setProductStatus(p.id, toSold ? 'SOLD' : 'AVAILABLE');
                toast(toSold ? 'Marked as sold' : 'Listing is available again', 'success');
                render();
            } catch (err) {
                toast(err.message, 'error');
            }
        }));
        group.append(statusBtn);
    }
    const deleteBtn = h('button', { type: 'button', class: 'btn btn-danger-outline', text: 'Delete' });
    deleteBtn.addEventListener('click', async () => {
        const ok = await confirmDialog({
            title: 'Delete this listing?',
            message: 'This permanently removes the listing, its photos and its conversations.',
            confirmLabel: 'Delete listing',
            danger: true,
        });
        if (!ok) return;
        await withBusy(deleteBtn, 'Deleting…', async () => {
            try {
                await api.deleteProduct(p.id);
                window.location.href = '/dashboard.html?deleted=1';
            } catch (err) {
                toast(err.message, 'error');
            }
        });
    });
    group.append(deleteBtn);
    box.append(group);
    return box;
}

function buyerActions(p) {
    const box = h('div', { class: 'detail-section' });
    if (!session.authenticated) {
        box.append(h('h3', { text: 'Interested?' }),
            h('p', { class: 'small muted', text: 'Log in to message the seller or save this item.' }),
            loginLink('Log in to contact seller'));
        return box;
    }

    // Wishlist toggle
    let saved = p.wishlisted;
    const heart = h('button', { type: 'button', class: 'btn btn-secondary', 'aria-pressed': String(saved) });
    const paintHeart = () => {
        heart.textContent = saved ? '♥ Saved to wishlist' : '♡ Save to wishlist';
        heart.setAttribute('aria-pressed', String(saved));
    };
    paintHeart();
    heart.addEventListener('click', () => withBusy(heart, 'Saving…', async () => {
        try {
            if (saved) {
                await api.removeFromWishlist(p.id);
                saved = false;
                toast('Removed from your wishlist', 'success');
            } else {
                await api.addToWishlist(p.id);
                saved = true;
                toast('Saved to your wishlist', 'success');
            }
        } catch (err) {
            toast(err.message, 'error');
        }
    }).then(paintHeart));

    box.append(h('div', { class: 'btn-group mb-2' }, heart));

    // Inquiry form
    if (p.status === 'AVAILABLE') {
        const alertSlot = h('div', { class: 'hidden mb-2' });
        const textarea = h('textarea', {
            id: 'inquiry-message', name: 'message', required: true, minlength: '2', maxlength: '2000',
        });
        textarea.value = `Hi, is "${p.title}" still available? I'm on ${session.user.campus}.`;
        const errorSlot = h('span', { class: 'error', 'data-for': 'message' });
        const submit = h('button', { type: 'submit', class: 'btn btn-primary', text: 'Send message' });
        const form = h('form', { class: 'form', novalidate: true },
            h('h3', { class: 'mb-0', text: 'Message the seller' }),
            alertSlot,
            h('div', { class: 'field' },
                h('label', { for: 'inquiry-message', text: 'Your message' }),
                textarea, errorSlot,
                h('span', { class: 'hint', text: 'Your email and phone are not shared. Replies appear in your inbox.' })),
            h('div', {}, submit));
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            clearFieldErrors(form);
            hideAlert(alertSlot);
            const message = textarea.value.trim();
            if (message.length < 2) {
                textarea.setAttribute('aria-invalid', 'true');
                errorSlot.textContent = 'Please write a short message.';
                textarea.focus();
                return;
            }
            await withBusy(submit, 'Sending…', async () => {
                try {
                    const inquiry = await api.createInquiry(p.id, message);
                    clear(form).append(alertBox('Message sent! The seller will reply in your inbox.', 'success'),
                        h('a', { class: 'btn btn-secondary mt-1', href: `/inquiries.html?tab=sent&id=${encodeURIComponent(inquiry.id)}`, text: 'Open conversation' }));
                } catch (err) {
                    if (!applyFieldErrors(form, err)) showAlert(alertSlot, err.message);
                }
            });
        });
        box.append(form);
    } else {
        box.append(alertBox('This item has been sold, so the seller is no longer accepting messages.', 'info'));
    }

    const reportBtn = h('button', { type: 'button', class: 'btn btn-ghost btn-sm mt-2', text: 'Report this listing' });
    reportBtn.addEventListener('click', openReport);
    box.append(reportBtn);
    return box;
}

function render() {
    const p = product;
    document.title = `${p.title} — UniTrade`;
    crumb.textContent = p.title;

    const info = h('div', { class: 'card' },
        h('div', { class: 'detail-section' },
            h('div', { class: 'badge-row' },
                statusBadge(p.status),
                h('span', { class: 'badge badge-primary', text: labelFor(meta.categories, p.category) }),
                h('span', { class: 'badge', text: labelFor(meta.conditions, p.condition) })),
            h('h1', { class: 'mt-1', text: p.title }),
            h('div', { class: 'detail-price', text: formatPrice(p.price) }),
            h('p', { class: 'small muted mb-0', text: `${p.campus} · Listed ${formatDate(p.createdAt)}` })),
        h('div', { class: 'detail-section' },
            h('h3', { text: 'Description' }),
            h('p', { class: 'pre-wrap mb-0', text: p.description })),
        h('div', { class: 'detail-section' },
            h('h3', { text: 'Seller' }),
            h('div', { class: 'seller-row' },
                h('div', { class: 'avatar', 'aria-hidden': 'true', text: initials(p.seller.displayName) }),
                h('div', {},
                    h('div', { text: p.seller.displayName }),
                    h('div', { class: 'small muted', text: `${p.seller.campus} · Member since ${formatDate(p.seller.memberSince)}` })))),
        p.owner ? ownerActions(p) : buyerActions(p));

    clear(root).append(h('div', { class: 'detail-layout' }, gallery(p), info));
}

function openReport() {
    clearFieldErrors(reportForm);
    hideAlert(reportAlert);
    reportForm.reset();
    reportDialog.showModal();
}

reportForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearFieldErrors(reportForm);
    hideAlert(reportAlert);
    const reason = reportForm.elements.reason.value;
    if (!reason) {
        reportForm.elements.reason.setAttribute('aria-invalid', 'true');
        reportForm.querySelector('.error[data-for="reason"]').textContent = 'Choose a reason.';
        return;
    }
    const submit = document.getElementById('report-submit');
    await withBusy(submit, 'Submitting…', async () => {
        try {
            await api.reportProduct(product.id, { reason, details: reportForm.elements.details.value.trim() });
            reportDialog.close();
            toast('Thanks — the moderators will review this listing.', 'success');
        } catch (err) {
            if (!applyFieldErrors(reportForm, err)) showAlert(reportAlert, err.message);
        }
    });
});
document.getElementById('report-cancel').addEventListener('click', () => reportDialog.close());

async function init() {
    session = await renderLayout();
    if (!productId || !/^\d+$/.test(productId)) {
        clear(root).append(errorState({ status: 404, message: 'This listing link is invalid.' }));
        return;
    }
    clear(root).append(loadingState('Loading listing…'));
    try {
        [meta, product] = await Promise.all([getMeta(), api.getProduct(productId)]);
        fillSelect(reportForm.elements.reason, meta.reportReasons, { placeholder: 'Choose a reason' });
        render();
    } catch (err) {
        clear(root).append(errorState(err, init));
    }
}

init();
