import { api } from '../api.js';
import {
    renderLayout, getMeta, h, clear, fillSelect, productCard, loadingState, emptyState, errorState, toggleWishlist,
} from '../ui.js';

const PAGE_SIZE = 12;
const filterForm = document.getElementById('filter-form');
const searchForm = document.getElementById('search-form');
const sortSelect = document.getElementById('sort');
const results = document.getElementById('results');
const resultCount = document.getElementById('result-count');
const pagination = document.getElementById('pagination');
const filtersPanel = document.getElementById('filters');
const filtersToggle = document.getElementById('filters-toggle');
const priceError = filterForm.querySelector('.error[data-for="price"]');

let session = { authenticated: false };
let meta = null;
let savedIds = new Set();

/** Current search state lives in the URL, so results are shareable and survive refresh. */
function readState() {
    const p = new URLSearchParams(window.location.search);
    return {
        q: p.get('q') || '',
        category: p.get('category') || '',
        campus: p.get('campus') || '',
        condition: p.get('condition') || '',
        minPrice: p.get('minPrice') || '',
        maxPrice: p.get('maxPrice') || '',
        sort: p.get('sort') || 'newest',
        page: Math.max(0, parseInt(p.get('page') || '0', 10) || 0),
    };
}

function writeState(state) {
    const p = new URLSearchParams();
    Object.entries(state).forEach(([k, v]) => {
        if (v !== '' && v !== null && v !== undefined && !(k === 'page' && v === 0) && !(k === 'sort' && v === 'newest')) {
            p.set(k, v);
        }
    });
    const qs = p.toString();
    history.replaceState(null, '', qs ? `?${qs}` : window.location.pathname);
}

function syncFormFromState(state) {
    document.getElementById('q').value = state.q;
    filterForm.elements.category.value = state.category;
    filterForm.elements.campus.value = state.campus;
    filterForm.elements.condition.value = state.condition;
    filterForm.elements.minPrice.value = state.minPrice;
    filterForm.elements.maxPrice.value = state.maxPrice;
    sortSelect.value = state.sort;
}

function stateFromForm(page = 0) {
    return {
        q: document.getElementById('q').value.trim(),
        category: filterForm.elements.category.value,
        campus: filterForm.elements.campus.value,
        condition: filterForm.elements.condition.value,
        minPrice: filterForm.elements.minPrice.value.trim(),
        maxPrice: filterForm.elements.maxPrice.value.trim(),
        sort: sortSelect.value,
        page,
    };
}

function validPrices(state) {
    priceError.textContent = '';
    const min = state.minPrice === '' ? null : Number(state.minPrice);
    const max = state.maxPrice === '' ? null : Number(state.maxPrice);
    if ((min !== null && (Number.isNaN(min) || min < 0)) || (max !== null && (Number.isNaN(max) || max < 0))) {
        priceError.textContent = 'Prices must be positive numbers.';
        return false;
    }
    if (min !== null && max !== null && min > max) {
        priceError.textContent = 'Minimum price cannot exceed maximum price.';
        return false;
    }
    return true;
}

async function load(state) {
    if (!validPrices(state)) return;
    writeState(state);
    clear(results).append(loadingState('Searching listings…'));
    clear(pagination);
    resultCount.textContent = '';
    try {
        const page = await api.searchProducts({
            q: state.q, category: state.category, campus: state.campus, condition: state.condition,
            minPrice: state.minPrice, maxPrice: state.maxPrice, sort: state.sort, page: state.page, size: PAGE_SIZE,
        });
        renderResults(page, state);
    } catch (err) {
        clear(results).append(errorState(err, () => load(state)));
    }
}

function renderResults(page, state) {
    const total = page.totalElements;
    resultCount.textContent = total === 1 ? '1 listing found' : `${total} listings found`;
    if (page.content.length === 0) {
        const filtered = state.q || state.category || state.campus || state.condition || state.minPrice || state.maxPrice;
        clear(results).append(filtered
            ? emptyState('No matching listings', 'Try a different search term or remove some filters.')
            : emptyState('No listings yet', 'Be the first to list an item.', { href: '/sell.html', label: 'Sell an item' }));
        return;
    }
    clear(results).append(h('div', { class: 'grid' }, page.content.map((p) => productCard(p, {
        meta,
        wishlisted: savedIds.has(p.id),
        currentUserId: session.user ? session.user.id : undefined,
        onToggleWishlist: session.authenticated
            ? async (product, current) => {
                const saved = await toggleWishlist(product, current);
                if (saved) savedIds.add(product.id); else savedIds.delete(product.id);
                return saved;
            }
            : null,
    }))));

    if (page.totalPages > 1) {
        const prev = h('button', {
            type: 'button', class: 'btn btn-secondary btn-sm', disabled: page.page === 0,
            onclick: () => { load({ ...state, page: page.page - 1 }); window.scrollTo(0, 0); },
        }, '← Previous');
        const next = h('button', {
            type: 'button', class: 'btn btn-secondary btn-sm', disabled: page.page >= page.totalPages - 1,
            onclick: () => { load({ ...state, page: page.page + 1 }); window.scrollTo(0, 0); },
        }, 'Next →');
        clear(pagination).append(prev, h('span', { class: 'small muted', text: `Page ${page.page + 1} of ${page.totalPages}` }), next);
    }
}

async function init() {
    session = await renderLayout();
    try {
        meta = await getMeta();
    } catch (err) {
        clear(results).append(errorState(err, init));
        return;
    }
    fillSelect(filterForm.elements.category, meta.categories, { placeholder: 'All categories' });
    fillSelect(filterForm.elements.campus, meta.campuses, { placeholder: 'All campuses' });
    fillSelect(filterForm.elements.condition, meta.conditions, { placeholder: 'Any condition' });

    if (session.authenticated) {
        try {
            savedIds = new Set(await api.wishlistIds());
        } catch {
            savedIds = new Set();
        }
    }

    const state = readState();
    syncFormFromState(state);
    load(state);
}

filterForm.addEventListener('submit', (e) => {
    e.preventDefault();
    load(stateFromForm());
    if (window.matchMedia('(max-width: 900px)').matches) {
        filtersPanel.classList.remove('open');
        filtersToggle.setAttribute('aria-expanded', 'false');
    }
});
searchForm.addEventListener('submit', (e) => {
    e.preventDefault();
    load(stateFromForm());
});
sortSelect.addEventListener('change', () => load(stateFromForm()));
['category', 'campus', 'condition'].forEach((name) => {
    filterForm.elements[name].addEventListener('change', () => load(stateFromForm()));
});
document.getElementById('reset-filters').addEventListener('click', () => {
    filterForm.reset();
    document.getElementById('q').value = '';
    sortSelect.value = 'newest';
    load(stateFromForm());
});
filtersToggle.addEventListener('click', () => {
    const open = filtersPanel.classList.toggle('open');
    filtersToggle.setAttribute('aria-expanded', String(open));
});

init();
