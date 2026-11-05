import { api } from '../api.js';
import {
    renderLayout, getMeta, fillSelect, param, safeNext, clearFieldErrors, setFieldError, applyFieldErrors,
    showAlert, hideAlert, withBusy, formValues,
} from '../ui.js';

const form = document.getElementById('register-form');
const alertBox = document.getElementById('form-alert');
const submit = document.getElementById('register-submit');

const session = await renderLayout();
if (session.authenticated) {
    window.location.replace('/dashboard.html');
}

try {
    const meta = await getMeta();
    fillSelect(form.elements.campus, meta.campuses, { placeholder: 'Select your campus' });
} catch (err) {
    showAlert(alertBox, err.message);
}

/** Mirrors the server-side Bean Validation rules for instant feedback. The server re-validates everything. */
function validate(v) {
    const errors = {};
    if (!v.fullName || v.fullName.length < 2) errors.fullName = 'Enter your full name (at least 2 characters).';
    if (!v.email || !form.elements.email.checkValidity()) errors.email = 'Enter a valid email address.';
    if (!v.campus) errors.campus = 'Choose your campus.';
    if (v.phone && !/^[+]?[0-9 ]{10,15}$/.test(v.phone)) errors.phone = 'Enter a valid phone number (10-15 digits).';
    const pw = form.elements.password.value;
    if (pw.length < 8 || pw.length > 72) errors.password = 'Password must be 8-72 characters.';
    else if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) errors.password = 'Include at least one letter and one number.';
    if (form.elements.confirmPassword.value !== pw) errors.confirmPassword = 'Passwords do not match.';
    return errors;
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearFieldErrors(form);
    hideAlert(alertBox);

    const values = formValues(form);
    const errors = validate(values);
    if (Object.keys(errors).length) {
        Object.entries(errors).forEach(([field, msg]) => setFieldError(form, field, msg));
        form.querySelector('[aria-invalid="true"]').focus();
        return;
    }

    await withBusy(submit, 'Creating account…', async () => {
        try {
            await api.register({
                fullName: values.fullName,
                email: values.email,
                campus: values.campus,
                phone: values.phone || null,
                password: form.elements.password.value,
            });
            const next = param('next') ? `&next=${encodeURIComponent(safeNext(param('next')))}` : '';
            try {
                sessionStorage.setItem('unitrade.prefillEmail', values.email); // not put in the URL
            } catch {
                // storage unavailable (private mode); the user simply types the email again
            }
            window.location.href = `/login.html?registered=1${next}`;
        } catch (err) {
            if (!applyFieldErrors(form, err)) showAlert(alertBox, err.message);
        }
    });
});
