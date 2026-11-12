/*
 * Buscador de la pantalla de Asistencia.
 *
 * Pensado para usarse sin soltar el teclado: se escribe, se elige con las
 * flechas (o se deja el primer resultado) y Enter registra el ingreso. Si el
 * Enter llega antes que los resultados, como pasa al tipear un DNI de corrido,
 * queda anotado y se aplica apenas responde el servidor.
 */
(() => {
    const campo = document.getElementById('buscarSocioAsistencia');
    const lista = document.getElementById('resultadosAsistencia');
    const formulario = document.getElementById('formIngreso');
    const socioIdInput = document.getElementById('ingresoSocioId');

    // Cualquier formulario marcado se envia una sola vez: un doble clic no
    // tiene que generar dos pedidos.
    document.querySelectorAll('form[data-envio-unico]').forEach((form) => {
        form.addEventListener('submit', (evento) => {
            if (form.dataset.enviado === 'true') {
                evento.preventDefault();
                return;
            }
            form.dataset.enviado = 'true';
            const boton = form.querySelector('button[type="submit"]');
            if (boton) {
                boton.disabled = true;
            }
        });
    });

    // Despues de registrar, la respuesta y el buscador tienen que quedar a la
    // vista los dos, aunque la pantalla sea chica. El cursor vuelve al
    // buscador sin mover la pagina; solo se desplaza si alguno no entra.
    const respuesta = document.getElementById('respuestaIngreso');
    if (respuesta && campo) {
        campo.focus({ preventScroll: true });
        const panel = respuesta.getBoundingClientRect();
        const buscador = campo.getBoundingClientRect();
        if (panel.top < 0 || buscador.bottom > window.innerHeight) {
            respuesta.scrollIntoView({ block: 'start' });
        }
    }

    if (!campo || !lista || !formulario || !socioIdInput) {
        return;
    }

    const urlBusqueda = campo.dataset.buscarUrl;
    const ESPERA_MS = 250;
    const MINIMO_CARACTERES = 2;

    let resultados = [];
    let indiceActivo = -1;
    let temporizador = null;
    let numeroDeBusqueda = 0;
    let busquedaEnCurso = false;
    let enterPendiente = false;
    let enviando = false;

    function cerrarLista() {
        lista.innerHTML = '';
        lista.classList.remove('show');
        campo.setAttribute('aria-expanded', 'false');
        resultados = [];
        indiceActivo = -1;
    }

    function claseDeEstado(estado) {
        if (estado === 'ACTIVO') return 'status-badge-success';
        if (estado === 'MOROSO') return 'status-badge-warning';
        if (estado === 'INACTIVO') return 'status-badge-danger';
        return '';
    }

    function textoDeEstado(estado) {
        if (estado === 'ACTIVO') return 'Activo';
        if (estado === 'MOROSO') return 'Moroso';
        if (estado === 'INACTIVO') return 'Inactivo';
        return '-';
    }

    function marcarActivo(indice) {
        indiceActivo = indice;
        lista.querySelectorAll('.cobro-resultado-item').forEach((item, posicion) => {
            const activo = posicion === indice;
            item.classList.toggle('is-active', activo);
            item.setAttribute('aria-selected', activo ? 'true' : 'false');
            if (activo) {
                item.scrollIntoView({ block: 'nearest' });
            }
        });
    }

    function registrar(socio) {
        if (enviando || !socio) {
            return;
        }
        enviando = true;
        socioIdInput.value = socio.id;
        campo.disabled = true;
        cerrarLista();
        formulario.submit();
    }

    function mostrarResultados(datos) {
        lista.innerHTML = '';
        resultados = Array.isArray(datos) ? datos : [];
        lista.classList.add('show');
        campo.setAttribute('aria-expanded', 'true');

        if (resultados.length === 0) {
            const vacio = document.createElement('div');
            vacio.className = 'cobro-resultado-vacio';
            vacio.textContent = 'No se encontraron socios con ese nombre o DNI.';
            lista.appendChild(vacio);
            indiceActivo = -1;
            return;
        }

        resultados.forEach((socio, posicion) => {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'cobro-resultado-item asistencia-resultado';
            item.setAttribute('role', 'option');

            const nombre = document.createElement('span');
            nombre.className = 'cobro-resultado-nombre';
            nombre.textContent = socio.nombreCompleto;

            const dni = document.createElement('span');
            dni.className = 'cobro-resultado-dni';
            dni.textContent = 'DNI ' + socio.dni;

            const estado = document.createElement('span');
            estado.className = 'status-badge ' + claseDeEstado(socio.estado);
            estado.textContent = textoDeEstado(socio.estado);

            item.append(nombre, dni, estado);
            item.addEventListener('click', () => registrar(socio));
            item.addEventListener('mousemove', () => {
                if (indiceActivo !== posicion) {
                    marcarActivo(posicion);
                }
            });
            lista.appendChild(item);
        });
        marcarActivo(0);
    }

    function buscar(texto) {
        const estaBusqueda = ++numeroDeBusqueda;
        busquedaEnCurso = true;
        fetch(urlBusqueda + '?q=' + encodeURIComponent(texto), { headers: { 'Accept': 'application/json' } })
            .then((respuestaHttp) => {
                if (!respuestaHttp.ok) {
                    throw new Error('Respuesta inesperada: ' + respuestaHttp.status);
                }
                return respuestaHttp.json();
            })
            .then((datos) => {
                // Si mientras tanto se siguio escribiendo, esta respuesta ya es vieja.
                if (estaBusqueda !== numeroDeBusqueda) {
                    return;
                }
                busquedaEnCurso = false;
                mostrarResultados(datos);
                if (enterPendiente) {
                    enterPendiente = false;
                    if (resultados.length > 0) {
                        registrar(resultados[0]);
                    }
                }
            })
            .catch(() => {
                if (estaBusqueda !== numeroDeBusqueda) {
                    return;
                }
                busquedaEnCurso = false;
                enterPendiente = false;
                lista.innerHTML = '';
                const aviso = document.createElement('div');
                aviso.className = 'cobro-resultado-vacio';
                aviso.textContent = 'No se pudo buscar. Revisá la conexión con el sistema e intentá de nuevo.';
                lista.appendChild(aviso);
                lista.classList.add('show');
                resultados = [];
                indiceActivo = -1;
            });
    }

    function programarBusqueda(inmediata) {
        clearTimeout(temporizador);
        temporizador = null;
        const texto = campo.value.trim();
        if (texto.length < MINIMO_CARACTERES) {
            numeroDeBusqueda++; // descarta cualquier respuesta en camino
            busquedaEnCurso = false;
            enterPendiente = false;
            cerrarLista();
            return;
        }
        if (inmediata) {
            buscar(texto);
        } else {
            temporizador = setTimeout(() => {
                temporizador = null;
                buscar(texto);
            }, ESPERA_MS);
        }
    }

    campo.addEventListener('input', () => {
        enterPendiente = false;
        programarBusqueda(false);
    });

    campo.addEventListener('keydown', (evento) => {
        if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
            if (resultados.length === 0) {
                return;
            }
            evento.preventDefault();
            const paso = evento.key === 'ArrowDown' ? 1 : -1;
            marcarActivo((indiceActivo + paso + resultados.length) % resultados.length);
        } else if (evento.key === 'Enter') {
            evento.preventDefault();
            if (temporizador !== null || busquedaEnCurso) {
                // Todavia no hay resultados para lo que se escribio: se busca
                // ya mismo y el Enter se aplica cuando lleguen.
                enterPendiente = true;
                if (temporizador !== null) {
                    programarBusqueda(true);
                }
            } else if (indiceActivo >= 0) {
                registrar(resultados[indiceActivo]);
            }
        } else if (evento.key === 'Escape') {
            enterPendiente = false;
            cerrarLista();
        }
    });

    document.addEventListener('click', (evento) => {
        if (!lista.contains(evento.target) && evento.target !== campo) {
            lista.classList.remove('show');
            campo.setAttribute('aria-expanded', 'false');
        }
    });

    campo.addEventListener('focus', () => {
        if (resultados.length > 0) {
            lista.classList.add('show');
            campo.setAttribute('aria-expanded', 'true');
        }
    });

    // Al volver con el boton "atras" el navegador puede restaurar la pagina
    // con el buscador deshabilitado por el envio anterior.
    window.addEventListener('pageshow', () => {
        enviando = false;
        campo.disabled = false;
    });
})();
