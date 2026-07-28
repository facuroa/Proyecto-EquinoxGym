(() => {
    const modalElement = document.getElementById('confirmarCobroModal');
    const confirmButton = document.getElementById('confirmarCobroAccion');

    if (!modalElement || !confirmButton || typeof bootstrap === 'undefined') {
        return;
    }

    const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
    const memberOutput = document.getElementById('confirmarCobroSocio');
    const dueOutput = document.getElementById('confirmarCobroVencimiento');
    const paymentMethodOutput = document.getElementById('confirmarCobroMedio');
    const amountOutput = document.getElementById('confirmarCobroMonto');
    let pendingForm = null;

    const fieldValue = (form, name) => {
        const field = form.querySelector(`[name="${name}"]`);
        return field ? field.value.trim() : '';
    };

    const selectedText = (form, name) => {
        const field = form.querySelector(`[name="${name}"]`);
        return field && field.selectedIndex >= 0 ? field.options[field.selectedIndex].text.trim() : '';
    };

    const memberName = (form) => {
        if (form.dataset.paymentMember) {
            return form.dataset.paymentMember;
        }

        const quotaText = selectedText(form, 'cuotaId');
        if (quotaText) {
            return quotaText.split(' - $')[0].trim();
        }

        return [fieldValue(form, 'nombre'), fieldValue(form, 'apellido')]
            .filter(Boolean)
            .join(' ') || 'Socio nuevo';
    };

    const dueDate = (form) => {
        if (form.dataset.paymentDue) {
            return form.dataset.paymentDue;
        }

        const quotaText = selectedText(form, 'cuotaId');
        const match = quotaText.match(/Vence:\s*([^()]+)/i);
        return match ? match[1].trim() : 'Se calculará según el plan';
    };

    const amount = (form) => {
        const value = fieldValue(form, 'monto') || fieldValue(form, 'montoInicial');
        if (!value) {
            return 'Precio vigente del plan';
        }

        const number = Number(value);
        return Number.isFinite(number)
            ? number.toLocaleString('es-AR', { style: 'currency', currency: 'ARS' })
            : `$ ${value}`;
    };

    const paymentMethod = (form) => selectedText(form, 'medioPago')
        || selectedText(form, 'medioPagoInicial')
        || 'Sin especificar';

    document.querySelectorAll('form[data-confirm-payment]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (form.dataset.paymentConfirmed === 'true') {
                return;
            }

            const optionalCheckboxId = form.dataset.paymentOptionalCheckbox;
            const optionalCheckbox = optionalCheckboxId ? document.getElementById(optionalCheckboxId) : null;
            if (optionalCheckbox && !optionalCheckbox.checked) {
                return;
            }

            const optionalPaymentMethod = optionalCheckbox
                ? form.querySelector('[name="medioPagoInicial"]')
                : null;
            if (optionalPaymentMethod && !optionalPaymentMethod.value) {
                event.preventDefault();
                optionalPaymentMethod.setCustomValidity('Seleccioná el medio de pago para registrar el cobro.');
                optionalPaymentMethod.reportValidity();
                optionalPaymentMethod.addEventListener('change', () => {
                    optionalPaymentMethod.setCustomValidity('');
                }, { once: true });
                return;
            }

            event.preventDefault();
            pendingForm = form;
            memberOutput.textContent = memberName(form);
            dueOutput.textContent = dueDate(form);
            paymentMethodOutput.textContent = paymentMethod(form);
            amountOutput.textContent = amount(form);
            modal.show();
        });
    });

    confirmButton.addEventListener('click', () => {
        if (!pendingForm) {
            return;
        }

        pendingForm.dataset.paymentConfirmed = 'true';
        modal.hide();
        pendingForm.requestSubmit();
    });

    modalElement.addEventListener('hidden.bs.modal', () => {
        if (pendingForm && pendingForm.dataset.paymentConfirmed !== 'true') {
            pendingForm = null;
        }
    });
})();
