document.addEventListener('DOMContentLoaded', function () {
    const modalEl = document.getElementById('confirmDeleteModal');
    if (!modalEl) return;

    const modal = new bootstrap.Modal(modalEl);
    const mensajeEl = document.getElementById('confirmDeleteMessage');
    const formularioEl = document.getElementById('confirmDeleteForm');
    const tituloEl = document.getElementById('confirmDeleteTitle');
    const botonEl = document.getElementById('confirmDeleteButton');

    // Textos por defecto: los usa el borrado, que es el caso mas comun.
    const TITULO_DEFECTO = tituloEl ? tituloEl.textContent : 'Confirmar eliminación';
    const BOTON_DEFECTO = botonEl ? botonEl.textContent : 'Sí, eliminar';
    const CLASE_DEFECTO = botonEl ? botonEl.className : '';

    document.querySelectorAll('[data-confirm-delete]').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            const url = this.getAttribute('href');
            const mensaje = this.getAttribute('data-mensaje')
                || '¿Seguro que querés eliminar este registro? Esta acción no se puede deshacer.';

            mensajeEl.textContent = mensaje;
            formularioEl.setAttribute('action', url);

            // La misma ventana sirve para dar de baja o reactivar, no solo
            // para eliminar: cada boton puede traer su propio texto.
            if (tituloEl) {
                tituloEl.textContent = this.getAttribute('data-titulo') || TITULO_DEFECTO;
            }
            if (botonEl) {
                botonEl.textContent = this.getAttribute('data-confirmar') || BOTON_DEFECTO;
                botonEl.className = this.getAttribute('data-boton-clase') || CLASE_DEFECTO;
            }

            modal.show();
        });
    });
});
