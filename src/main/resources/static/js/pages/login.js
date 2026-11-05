import { api } from '../api.js';
import {
    renderLayout, param, safeNext, clearFieldErrors, setFieldError, applyFieldErrors, showAlert, hideAlert, withBusy,
} from '../ui.js';

const form = document.getElementById('login-form');
const alertBox = document.getElementById('form-alert');
const submit = document.getElementById('login-submit');
const next = safeNext(param('next'));

const session = await renderLayout();
if (session.authenticated) {
    window.location.replace(next);
}

if (param('registered') === '1') {
    showAlert(alertBox, 'Your account was created. Log in to get started.', 'success');
}
if (param('next')) {
    document.getElementById('register-link').href = `/register.html?next=${encodeURIComponent(next)}`;
}
try {
    const prefill = sessionStorage.getItem('unitrade.prefillEmail');
    if (prefill) {
        form.elements.email.value = prefill;
        sessionStorage.removeItem('unitrade.prefillEmail');
        form.elements.password.focus();
    }
} catch {
    // sessionStorage unavailable; nothing to prefill
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearFieldErrors(form);
    hideAlert(alertBox);

    const email = form.elements.email.value.trim();
    const password = form.elements.password.value;
    let valid = true;
    if (!email || !form.elements.email.checkValidity()) {
        setFieldError(form, 'email', 'Enter a valid email address.');
        valid = false;
    }
    if (!password) {
        setFieldError(form, 'password', 'Enter your password.');
        valid = false;
    }
    if (!valid) {
        form.querySelector('[aria-invalid="true"]').focus();
        return;
    }

    await withBusy(submit, 'Logging in…', async () => {
        try {
            await api.login(email, password);
            window.location.href = next;
        } catch (err) {
            if (!applyFieldErrors(form, err)) showAlert(alertBox, err.message);
            form.elements.password.value = '';
        }
    });
});
