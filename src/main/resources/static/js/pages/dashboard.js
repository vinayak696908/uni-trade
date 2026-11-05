import { api } from '../api.js';
import {
    renderLayout, requireAuth, getMeta, fillSelect, h, clear, param, formatPrice, timeAgo, statusBadge, productImage,
    loadingState, emptyState, errorState, toast, withBusy, confirmDialog, showAlert, hideAlert, clearFieldErrors,
    setFieldError, applyFieldErrors, formValues,
} from '../ui.js';

const statsEl = document.getElementById('stats');
const listingsEl = document.getElementById('listings');
const filterSelect = document.getElementById('listing-filter');
const profileForm = document.getElementById('profile-form');
const profileAlert = document.getElementById('profile-alert');
const pageAlert = document.getElementById('page-alert');

let listings = [];

function renderStats() {
    const count = (s) => listings.filter((p) => p.status === s).length;
    const stat = (value, label) => h('div', { class: 'card stat' },
        h('div', { class: 'stat-value', text: String(value) }), h('div', { class: 'stat-label', text: label }));
    clear(statsEl).append(
        stat(listings.length, 'Total listings'),
        stat(count('AVAILABLE'), 'Available'),
        stat(count('SOLD'), 'Sold'),
        stat(count('HIDDEN'), 'Hidden by moderators'));
}

function listingRow(p) {
    const actions = h('div', { class: 'btn-group' },
        h('a', { class: 'btn btn-secondary btn-sm', href: `/product.html?id=${encodeURIComponent(p.id)}`, text: 'View' }),
        h('a', { class: 'btn btn-secondary btn-sm', href: `/sell.html?id=${encodeURIComponent(p.id)}`, text: 'Edit' }));

    if (p.status !== 'HIDDEN') {
        const toSold = p.status === 'AVAILABLE';
        const statusBtn = h('button', { type: 'button', class: 'btn btn-secondary btn-sm', text: toSold ? 'Mark sold' : 'Relist' });
        statusBtn.addEventListener('click', () => withBusy(statusBtn, 'Saving…', async () => {
            try {
                const updated = await api.setProductStatus(p.id, toSold ? 'SOLD' : 'AVAILABLE');
                p.status = updated.status;
                toast(toSold ? 'Marked as sold' : 'Listing is available again', 'success');
                render();
            } catch (err) {
                toast(err.message, 'error');
            }
        }));
        actions.append(statusBtn);
    }

    const deleteBtn = h('button', { type: 'button', class: 'btn btn-danger-outline btn-sm', text: 'Delete' });
    deleteBtn.addEventListener('click', async () => {
        const ok = await confirmDialog({
            title: `Delete “${p.title}”?`,
            message: 'This permanently removes the listing, its photos and related conversations.',
            confirmLabel: 'Delete listing', danger: true,
        });
        if (!ok) return;
        await withBusy(deleteBtn, 'Deleting…', async () => {
            try {
                await api.deleteProduct(p.id);
                listings = listings.filter((x) => x.id !== p.id);
                toast('Listing deleted', 'success');
                render();
            } catch (err) {
                toast(err.message, 'error');
            }
        });
    });
    actions.append(deleteBtn);

    return h('div', { class: 'list-item' },
        productImage(p.thumbnailUrl, p.category, ''),
        h('div', {},
            h('a', { class: 'title', href: `/product.html?id=${encodeURIComponent(p.id)}`, text: p.title }),
            h('div', { class: 'small muted' }, `${formatPrice(p.price)} · ${p.campus} · listed ${timeAgo(p.createdAt)} `, statusBadge(p.status))),
        actions);
}

function render() {
    renderStats();
    const filter = filterSelect.value;
    const visible = filter ? listings.filter((p) => p.status === filter) : listings;
    if (listings.length === 0) {
        clear(listingsEl).append(h('div', { class: 'card-body' },
            emptyState('You have no listings yet', 'Sell your old books or gadgets to fellow students.', { href: '/sell.html', label: 'Create your first listing' })));
        return;
    }
    if (visible.length === 0) {
        clear(listingsEl).append(h('div', { class: 'card-body' }, emptyState('Nothing here', 'No listings match this filter.')));
        return;
    }
    clear(listingsEl).append(h('div', { class: 'list' }, visible.map(listingRow)));
}

async function loadListings() {
    clear(listingsEl).append(loadingState('Loading your listings…'));
    try {
        listings = await api.myListings();
        render();
    } catch (err) {
        clear(listingsEl).append(h('div', { class: 'card-body' }, errorState(err, loadListings)));
    }
}

function fillProfile(user) {
    document.getElementById('greeting').textContent = `Hi, ${user.displayName}`;
    document.getElementById('p-email').value = user.email;
    profileForm.elements.fullName.value = user.fullName;
    profileForm.elements.campus.value = user.campus;
    profileForm.elements.phone.value = user.phone || '';
    document.getElementById('p-display').textContent = `Shown to others as “${user.displayName}”.`;
}

profileForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearFieldErrors(profileForm);
    hideAlert(profileAlert);
    const v = formValues(profileForm);
    let ok = true;
    if (!v.fullName || v.fullName.length < 2) { setFieldError(profileForm, 'fullName', 'Enter your full name.'); ok = false; }
    if (!v.campus) { setFieldError(profileForm, 'campus', 'Choose your campus.'); ok = false; }
    if (v.phone && !/^[+]?[0-9 ]{10,15}$/.test(v.phone)) { setFieldError(profileForm, 'phone', 'Enter a valid phone number.'); ok = false; }
    if (!ok) return;
    const button = document.getElementById('profile-submit');
    await withBusy(button, 'Saving…', async () => {
        try {
            const updated = await api.updateProfile({ fullName: v.fullName, campus: v.campus, phone: v.phone || null });
            fillProfile(updated);
            showAlert(profileAlert, 'Profile saved.', 'success');
        } catch (err) {
            if (!applyFieldErrors(profileForm, err)) showAlert(profileAlert, err.message);
        }
    });
});

filterSelect.addEventListener('change', render);

async function init() {
    await renderLayout();
    const sessionUser = await requireAuth();
    if (!sessionUser) return;
    if (param('deleted') === '1') showAlert(pageAlert, 'Your listing was deleted.', 'success');
    try {
        const [meta, me] = await Promise.all([getMeta(), api.me()]);
        fillSelect(profileForm.elements.campus, meta.campuses);
        fillProfile(me);
        if (me.role === 'ADMIN') document.getElementById('admin-link').classList.remove('hidden');
    } catch (err) {
        showAlert(profileAlert, err.message);
    }
    loadListings();
}

init();
