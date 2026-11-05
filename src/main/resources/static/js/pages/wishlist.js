import { api } from '../api.js';
import {
    renderLayout, requireAuth, getMeta, h, clear, productCard, loadingState, emptyState, errorState, toast, withBusy,
} from '../ui.js';

const container = document.getElementById('wishlist');
let items = [];
let meta = null;

function render() {
    if (items.length === 0) {
        clear(container).append(emptyState('Your wishlist is empty', 'Tap the heart on any listing to save it here.',
            { href: '/marketplace.html', label: 'Browse the marketplace' }));
        return;
    }
    const grid = h('div', { class: 'grid' });
    for (const p of items) {
        const card = productCard(p, { meta });
        const removeBtn = h('button', { type: 'button', class: 'btn btn-secondary btn-sm', text: 'Remove' });
        removeBtn.addEventListener('click', () => withBusy(removeBtn, 'Removing…', async () => {
            try {
                await api.removeFromWishlist(p.id);
                items = items.filter((x) => x.id !== p.id);
                toast('Removed from your wishlist', 'success');
                render();
            } catch (err) {
                toast(err.message, 'error');
            }
        }));
        card.append(h('div', { class: 'product-info' }, removeBtn));
        grid.append(card);
    }
    clear(container).append(grid);
}

async function load() {
    clear(container).append(loadingState('Loading your wishlist…'));
    try {
        [meta, items] = await Promise.all([getMeta(), api.wishlist()]);
        render();
    } catch (err) {
        clear(container).append(errorState(err, load));
    }
}

await renderLayout();
if (await requireAuth()) load();
