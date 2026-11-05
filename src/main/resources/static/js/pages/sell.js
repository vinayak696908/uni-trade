import { api } from '../api.js';
import {
    renderLayout, requireAuth, getMeta, fillSelect, param, h, clear, loadingState, errorState, toast,
    clearFieldErrors, setFieldError, applyFieldErrors, showAlert, hideAlert, withBusy, formValues, confirmDialog,
} from '../ui.js';

const MAX_IMAGES = 5;
const MAX_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const form = document.getElementById('sell-form');
const alertBox = document.getElementById('form-alert');
const loading = document.getElementById('sell-loading');
const submit = document.getElementById('sell-submit');
const fileInput = document.getElementById('images');
const existingWrap = document.getElementById('existing-images');
const newWrap = document.getElementById('new-images');
const descCount = document.getElementById('desc-count');
const imageError = form.querySelector('.error[data-for="images"]');

const editId = param('id');
let existingImages = [];
let pendingFiles = [];
let previewUrls = [];

function renderExisting() {
    clear(existingWrap);
    existingImages.forEach((img, index) => {
        const removeBtn = h('button', { type: 'button', text: 'Remove', 'aria-label': `Remove photo ${index + 1}` });
        removeBtn.addEventListener('click', async () => {
            const ok = await confirmDialog({
                title: 'Remove this photo?', message: 'The photo is deleted from your listing immediately.',
                confirmLabel: 'Remove photo', danger: true,
            });
            if (!ok) return;
            removeBtn.disabled = true;
            try {
                const updated = await api.deleteImage(editId, img.id);
                existingImages = updated.images;
                renderExisting();
                toast('Photo removed', 'success');
            } catch (err) {
                removeBtn.disabled = false;
                toast(err.message, 'error');
            }
        });
        existingWrap.append(h('div', { class: 'image-preview' }, h('img', { src: img.url, alt: `Photo ${index + 1}` }), removeBtn));
    });
}

function renderPending() {
    previewUrls.forEach((u) => URL.revokeObjectURL(u));
    previewUrls = [];
    clear(newWrap);
    pendingFiles.forEach((file, index) => {
        const url = URL.createObjectURL(file);
        previewUrls.push(url);
        newWrap.append(h('div', { class: 'image-preview' },
            h('img', { src: url, alt: `New photo: ${file.name}` }),
            h('button', {
                type: 'button', text: 'Remove', 'aria-label': `Remove ${file.name}`,
                onclick: () => { pendingFiles.splice(index, 1); renderPending(); },
            })));
    });
}

fileInput.addEventListener('change', () => {
    imageError.textContent = '';
    const chosen = Array.from(fileInput.files || []);
    const rejected = [];
    for (const file of chosen) {
        if (!ALLOWED_TYPES.includes(file.type)) {
            rejected.push(`${file.name}: only JPEG, PNG or WebP`);
        } else if (file.size > MAX_BYTES) {
            rejected.push(`${file.name}: larger than 5 MB`);
        } else if (existingImages.length + pendingFiles.length >= MAX_IMAGES) {
            rejected.push(`${file.name}: a listing can have at most ${MAX_IMAGES} photos`);
        } else {
            pendingFiles.push(file);
        }
    }
    fileInput.value = '';
    if (rejected.length) imageError.textContent = rejected.join('. ');
    renderPending();
});

form.elements.description.addEventListener('input', () => {
    descCount.textContent = String(form.elements.description.value.length);
});

function validate(v) {
    const errors = {};
    if (!v.title || v.title.length < 3) errors.title = 'Title must be at least 3 characters.';
    if (!v.category) errors.category = 'Choose a category.';
    if (!v.condition) errors.condition = 'Choose the condition.';
    const price = Number(v.price);
    if (!v.price || Number.isNaN(price)) errors.price = 'Enter a price in rupees.';
    else if (price < 1) errors.price = 'Price must be at least ₹1.';
    else if (price > 1000000) errors.price = 'Price must not exceed ₹10,00,000.';
    else if (!/^\d+(\.\d{1,2})?$/.test(v.price)) errors.price = 'Use at most 2 decimal places.';
    if (!v.campus) errors.campus = 'Choose a campus.';
    if (!v.description || v.description.length < 10) errors.description = 'Description must be at least 10 characters.';
    return errors;
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearFieldErrors(form);
    hideAlert(alertBox);

    const v = formValues(form);
    const errors = validate(v);
    if (Object.keys(errors).length) {
        Object.entries(errors).forEach(([field, msg]) => setFieldError(form, field, msg));
        form.querySelector('[aria-invalid="true"]').focus();
        return;
    }
    const payload = {
        title: v.title, description: v.description, category: v.category, condition: v.condition,
        price: v.price, campus: v.campus,
    };

    await withBusy(submit, editId ? 'Saving…' : 'Publishing…', async () => {
        let saved;
        try {
            saved = editId ? await api.updateProduct(editId, payload) : await api.createProduct(payload);
        } catch (err) {
            if (!applyFieldErrors(form, err)) showAlert(alertBox, err.message);
            return;
        }
        if (pendingFiles.length) {
            try {
                await api.uploadImages(saved.id, pendingFiles);
            } catch (err) {
                // The listing itself was saved; send the user to edit mode to retry the photos.
                if (!editId) {
                    window.location.href = `/sell.html?id=${encodeURIComponent(saved.id)}&photos=failed`;
                    return;
                }
                showAlert(alertBox, `Your changes were saved, but the photos could not be uploaded: ${err.message}`);
                return;
            }
        }
        window.location.href = `/product.html?id=${encodeURIComponent(saved.id)}`;
    });
});

async function init() {
    const user = await requireAuth();
    if (!user) return;
    loading.append(loadingState(editId ? 'Loading your listing…' : 'Preparing form…'));
    try {
        const meta = await getMeta();
        fillSelect(form.elements.category, meta.categories, { placeholder: 'Select a category' });
        fillSelect(form.elements.condition, meta.conditions, { placeholder: 'Select condition' });
        fillSelect(form.elements.campus, meta.campuses, { placeholder: 'Select campus', selected: user.campus });

        if (editId) {
            if (!/^\d+$/.test(editId)) throw Object.assign(new Error('This listing link is invalid.'), { status: 404 });
            const p = await api.getProduct(editId);
            if (!p.owner && user.role !== 'ADMIN') {
                throw Object.assign(new Error('You can only edit your own listings.'), { status: 403 });
            }
            document.title = 'Edit listing — UniTrade';
            document.getElementById('sell-heading').textContent = 'Edit listing';
            document.getElementById('sell-subheading').textContent = 'Update the details or photos of your listing.';
            submit.textContent = 'Save changes';
            document.getElementById('sell-cancel').href = `/product.html?id=${encodeURIComponent(p.id)}`;
            form.elements.title.value = p.title;
            form.elements.category.value = p.category;
            form.elements.condition.value = p.condition;
            form.elements.price.value = p.price;
            form.elements.campus.value = p.campus;
            form.elements.description.value = p.description;
            descCount.textContent = String(p.description.length);
            existingImages = p.images;
            renderExisting();
        }
        clear(loading);
        form.classList.remove('hidden');
        if (param('photos') === 'failed') {
            showAlert(alertBox, 'Your listing was published, but the photos could not be uploaded. '
                + 'Check that each photo is a JPEG, PNG or WebP under 5 MB and add them again below.', 'warning');
        }
    } catch (err) {
        clear(loading).append(errorState(err, () => window.location.reload()));
    }
}

await renderLayout();
init();
