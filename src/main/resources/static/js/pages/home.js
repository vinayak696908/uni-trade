import { api } from '../api.js';
import {
    renderLayout, getMeta, h, clear, placeholderFor, productCard, loadingState, emptyState, errorState, toggleWishlist,
} from '../ui.js';

const categoryGrid = document.getElementById('category-grid');
const recent = document.getElementById('recent');

async function renderCategories() {
    try {
        const meta = await getMeta();
        clear(categoryGrid).append(...meta.categories.map((c) => h('a', {
            class: 'category-tile',
            href: `/marketplace.html?category=${encodeURIComponent(c.value)}`,
        }, h('img', { src: placeholderFor(c.value), alt: '' }), h('span', { text: c.label }))));
    } catch (err) {
        clear(categoryGrid).append(errorState(err, renderCategories));
    }
}

async function renderRecent(session) {
    clear(recent).append(loadingState('Loading latest listings…'));
    try {
        const [meta, page, savedIds] = await Promise.all([
            getMeta(),
            api.searchProducts({ sort: 'newest', size: 8 }),
            session.authenticated ? api.wishlistIds().catch(() => []) : Promise.resolve([]),
        ]);
        if (page.content.length === 0) {
            clear(recent).append(emptyState('No listings yet', 'Be the first to sell something on UniTrade.',
                { href: '/sell.html', label: 'Sell an item' }));
            return;
        }
        const saved = new Set(savedIds);
        const grid = h('div', { class: 'grid' }, page.content.map((p) => productCard(p, {
            meta,
            wishlisted: saved.has(p.id),
            currentUserId: session.user ? session.user.id : undefined,
            onToggleWishlist: session.authenticated ? toggleWishlist : null,
        })));
        clear(recent).append(grid);
    } catch (err) {
        clear(recent).append(errorState(err, () => renderRecent(session)));
    }
}

const session = await renderLayout();
renderCategories();
renderRecent(session);
