/*
 * Evita que un formulario se envie dos veces por un doble clic o un doble
 * Enter. Con la PC del mostrador un poco lenta, el segundo clic llegaba a
 * crear un socio repetido o a cobrar dos veces.
 *
 * Escucha en el documento, despues de los scripts de cada pantalla: si uno
 * de ellos frena el envio (por ejemplo para pedir una confirmacion), este
 * no lo cuenta como enviado.
 */
(() => {
    const MARCA = 'envioEnCurso';

    document.addEventListener('submit', (evento) => {
        const form = evento.target;
        if (!(form instanceof HTMLFormElement) || (form.method || '').toLowerCase() !== 'post') {
            return;
        }
        if (form.dataset[MARCA] === 'true') {
            evento.preventDefault();
            return;
        }
        if (evento.defaultPrevented) {
            return;
        }
        form.dataset[MARCA] = 'true';
        // Se deshabilita despues de que el navegador arma los datos del envio.
        setTimeout(() => {
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach((boton) => {
                boton.disabled = true;
            });
        }, 0);
    });

    // Al volver con "atras", el navegador puede mostrar la pagina como quedo:
    // los formularios tienen que volver a funcionar.
    window.addEventListener('pageshow', (evento) => {
        if (!evento.persisted) {
            return;
        }
        document.querySelectorAll('form').forEach((form) => {
            delete form.dataset[MARCA];
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach((boton) => {
                boton.disabled = false;
            });
        });
    });
})();
