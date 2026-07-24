(() => {
    const form = document.getElementById('seguimientoForm');
    const memberName = document.getElementById('seguimientoSocioNombre');
    const submitButton = form?.querySelector('button[type="submit"]');

    if (!form || !memberName) {
        return;
    }

    document.querySelectorAll('[data-socio-id]').forEach((button) => {
        button.addEventListener('click', () => {
            form.reset();
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.textContent = 'Guardar seguimiento';
            }
            form.action = `/morosidad/${encodeURIComponent(button.dataset.socioId)}/seguimientos`;
            memberName.textContent = button.dataset.socioNombre || 'Socio';
        });
    });

    form.addEventListener('submit', () => {
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = 'Guardando...';
        }
    });
})();
