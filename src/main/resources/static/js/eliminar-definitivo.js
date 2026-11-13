/*
 * Ventana de eliminacion definitiva. El boton queda deshabilitado hasta que
 * se escribe la palabra de confirmacion y un motivo; el servidor vuelve a
 * controlar las dos cosas.
 */
(() => {
    const modalElement = document.getElementById('eliminarDefinitivoModal');
    if (!modalElement || typeof bootstrap === 'undefined') {
        return;
    }

    const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
    const form = document.getElementById('eliminarDefinitivoForm');
    const titulo = document.getElementById('eliminarDefinitivoTitulo');
    const detalle = document.getElementById('eliminarDefinitivoDetalle');
    const motivo = document.getElementById('eliminarMotivo');
    const confirmacion = document.getElementById('eliminarConfirmacion');
    const boton = document.getElementById('eliminarDefinitivoBoton');
    const palabra = (form.dataset.palabra || 'ELIMINAR').toUpperCase();

    const actualizarBoton = () => {
        boton.disabled = confirmacion.value.trim().toUpperCase() !== palabra
            || motivo.value.trim().length < 5;
    };

    document.querySelectorAll('[data-eliminar-definitivo]').forEach((disparador) => {
        disparador.addEventListener('click', (evento) => {
            evento.preventDefault();
            form.setAttribute('action', disparador.dataset.url);
            titulo.textContent = disparador.dataset.titulo || 'Eliminar';
            detalle.textContent = disparador.dataset.detalle || 'Se va a eliminar el registro.';
            form.reset();
            actualizarBoton();
            modal.show();
        });
    });

    motivo.addEventListener('input', actualizarBoton);
    confirmacion.addEventListener('input', actualizarBoton);
    modalElement.addEventListener('shown.bs.modal', () => motivo.focus());
})();
