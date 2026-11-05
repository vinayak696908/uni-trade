// =====================================================================
// UniTrade API client — the only place that talks to the Spring Boot backend.
// - Sends cookies (session) with every request (same origin).
// - Attaches the CSRF token to every state-changing request and refreshes it
//   automatically once if the server reports it expired.
// - Turns non-2xx responses into ApiError with a human-readable message.
// =====================================================================

const DEFAULT_MESSAGES = {
    0: 'Cannot reach the UniTrade server. Check that the application is running.',
    400: 'Please check the information you entered.',
    401: 'Please log in to continue.',
    403: 'You do not have permission to do that.',
    404: 'We could not find what you were looking for.',
    409: 'This conflicts with existing data.',
    413: 'The file is too large.',
    415: 'Unsupported file or content type.',
    429: 'Too many attempts. Please wait a few minutes and try again.',
    500: 'Something went wrong on the server. Please try again.',
};

export class ApiError extends Error {
    constructor(status, body) {
        const message = (body && body.message) || DEFAULT_MESSAGES[status]
            || (status >= 500 ? DEFAULT_MESSAGES[500] : 'Request failed.');
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = body && body.code;
        this.fieldErrors = (body && body.fieldErrors) || {};
    }
}

let csrfPromise = null;

async function getCsrf(force = false) {
    if (!csrfPromise || force) {
        csrfPromise = fetch('/api/auth/csrf', {
            credentials: 'same-origin',
            headers: { Accept: 'application/json' },
            cache: 'no-store',
        }).then(async (res) => {
            if (!res.ok) throw new ApiError(res.status, null);
            return res.json();
        }).catch((err) => {
            csrfPromise = null;
            throw err instanceof ApiError ? err : new ApiError(0, null);
        });
    }
    return csrfPromise;
}

/** Forget the cached CSRF token (call after login/logout, when the server rotates it). */
export function resetCsrf() {
    csrfPromise = null;
}

function buildUrl(path, query) {
    const url = new URL(path, window.location.origin);
    if (query) {
        Object.entries(query).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
    }
    return url.pathname + url.search;
}

async function parseBody(res) {
    const type = res.headers.get('Content-Type') || '';
    if (type.includes('application/json')) {
        try {
            return await res.json();
        } catch {
            return null;
        }
    }
    return null;
}

/**
 * @param {string} method HTTP method
 * @param {string} path   e.g. '/api/products'
 * @param {{body?: any, query?: object, form?: FormData}} options
 */
export async function request(method, path, options = {}, isRetry = false) {
    const { body, query, form } = options;
    const headers = { Accept: 'application/json' };
    let payload;
    if (form instanceof FormData) {
        payload = form; // browser sets multipart boundary
    } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
    }

    const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method);
    if (unsafe) {
        const token = await getCsrf();
        headers[token.headerName] = token.token;
    }

    let res;
    try {
        res = await fetch(buildUrl(path, query), {
            method,
            headers,
            body: payload,
            credentials: 'same-origin',
            cache: 'no-store',
        });
    } catch {
        throw new ApiError(0, null);
    }

    const data = res.status === 204 ? null : await parseBody(res);
    if (!res.ok) {
        if (res.status === 403 && data && data.code === 'CSRF_INVALID' && !isRetry) {
            await getCsrf(true);
            return request(method, path, options, true);
        }
        throw new ApiError(res.status, data);
    }
    return data;
}

export const api = {
    get: (path, query) => request('GET', path, { query }),
    post: (path, body) => request('POST', path, { body }),
    put: (path, body) => request('PUT', path, { body }),
    patch: (path, body) => request('PATCH', path, { body }),
    del: (path) => request('DELETE', path),
    upload: (path, formData) => request('POST', path, { form: formData }),

    // ---- auth
    session: () => request('GET', '/api/auth/session'),
    register: (data) => request('POST', '/api/auth/register', { body: data }),
    login: async (email, password) => {
        const profile = await request('POST', '/api/auth/login', { body: { email, password } });
        resetCsrf();
        return profile;
    },
    logout: async () => {
        await request('POST', '/api/auth/logout');
        resetCsrf();
    },
    me: () => request('GET', '/api/auth/me'),
    updateProfile: (data) => request('PUT', '/api/users/me', { body: data }),
    myListings: () => request('GET', '/api/users/me/products'),

    // ---- reference data
    meta: () => request('GET', '/api/meta'),

    // ---- products
    searchProducts: (query) => request('GET', '/api/products', { query }),
    getProduct: (id) => request('GET', `/api/products/${encodeURIComponent(id)}`),
    createProduct: (data) => request('POST', '/api/products', { body: data }),
    updateProduct: (id, data) => request('PUT', `/api/products/${encodeURIComponent(id)}`, { body: data }),
    deleteProduct: (id) => request('DELETE', `/api/products/${encodeURIComponent(id)}`),
    setProductStatus: (id, status) => request('PATCH', `/api/products/${encodeURIComponent(id)}/status`, { body: { status } }),
    uploadImages: (id, files) => {
        const fd = new FormData();
        files.forEach((f) => fd.append('images', f));
        return request('POST', `/api/products/${encodeURIComponent(id)}/images`, { form: fd });
    },
    deleteImage: (id, imageId) => request('DELETE', `/api/products/${encodeURIComponent(id)}/images/${encodeURIComponent(imageId)}`),
    reportProduct: (id, data) => request('POST', `/api/products/${encodeURIComponent(id)}/reports`, { body: data }),

    // ---- wishlist
    wishlist: () => request('GET', '/api/wishlist'),
    wishlistIds: () => request('GET', '/api/wishlist/ids'),
    addToWishlist: (productId) => request('POST', `/api/wishlist/${encodeURIComponent(productId)}`),
    removeFromWishlist: (productId) => request('DELETE', `/api/wishlist/${encodeURIComponent(productId)}`),

    // ---- inquiries
    createInquiry: (productId, message) => request('POST', '/api/inquiries', { body: { productId, message } }),
    sentInquiries: () => request('GET', '/api/inquiries/sent'),
    receivedInquiries: () => request('GET', '/api/inquiries/received'),
    getInquiry: (id) => request('GET', `/api/inquiries/${encodeURIComponent(id)}`),
    replyInquiry: (id, message) => request('POST', `/api/inquiries/${encodeURIComponent(id)}/messages`, { body: { message } }),
    closeInquiry: (id) => request('PATCH', `/api/inquiries/${encodeURIComponent(id)}/close`),

    // ---- admin
    adminStats: () => request('GET', '/api/admin/stats'),
    adminReports: (status) => request('GET', '/api/admin/reports', { query: { status } }),
    adminResolveReport: (id, data) => request('PATCH', `/api/admin/reports/${encodeURIComponent(id)}`, { body: data }),
    adminProducts: (query) => request('GET', '/api/admin/products', { query }),
    adminSetProductStatus: (id, status) => request('PATCH', `/api/admin/products/${encodeURIComponent(id)}/status`, { body: { status } }),
    adminDeleteProduct: (id) => request('DELETE', `/api/admin/products/${encodeURIComponent(id)}`),
    adminUsers: (query) => request('GET', '/api/admin/users', { query }),
    adminSetUserEnabled: (id, enabled) => request('PATCH', `/api/admin/users/${encodeURIComponent(id)}/status`, { body: { enabled } }),
};
