document.addEventListener('DOMContentLoaded', function () {
    const modalEl = document.getElementById('confirmDeleteModal');
    if (!modalEl) return;

    const modal = new bootstrap.Modal(modalEl);
    const mensajeEl = document.getElementById('confirmDeleteMessage');
    const botonEl = document.getElementById('confirmDeleteButton');

    document.querySelectorAll('[data-confirm-delete]').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            const url = this.getAttribute('href');
            const mensaje = this.getAttribute('data-mensaje')
                || '¿Seguro que querés eliminar este registro? Esta acción no se puede deshacer.';

            mensajeEl.textContent = mensaje;
            botonEl.setAttribute('href', url);
            modal.show();
        });
    });
});
