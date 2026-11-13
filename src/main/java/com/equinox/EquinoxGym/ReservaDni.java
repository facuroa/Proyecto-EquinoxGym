package com.equinox.EquinoxGym;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Hace que las altas y los cambios de DNI se guarden de a uno.
 *
 * <p>El indice unico de la base es la proteccion definitiva contra socios
 * repetidos, pero MySQL no lo puede crear mientras haya duplicados cargados
 * de antes. Hasta que se limpien, dos pedidos casi simultaneos (un doble clic
 * en "Guardar") podian pasar los dos el control de DNI y crear dos socios.
 * Con este candado el segundo espera a que el primero termine de guardarse y
 * entonces ve el DNI ya ocupado.
 *
 * <p>La operacion tiene que abrir y cerrar su propia transaccion adentro del
 * candado: si la transaccion se confirmara despues de soltarlo, el siguiente
 * pedido no veria el socio recien creado.
 */
@Component
public class ReservaDni {

    private final ReentrantLock candado = new ReentrantLock(true);

    public <T> T ejecutar(Supplier<T> operacion) {
        candado.lock();
        try {
            return operacion.get();
        } finally {
            candado.unlock();
        }
    }
}
