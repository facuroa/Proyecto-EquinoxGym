# Hoja de ruta de Equinox Gym

Esta hoja de ruta fija el orden de trabajo. No se cambia una fase ni se agregan funciones fuera de alcance sin acordarlo primero.

## Principios

- Priorizar tareas reales de recepción y administración.
- Evitar información repetida: cada pantalla debe tener una función clara.
- Mantener el diseño premium actual, con legibilidad antes que efectos visuales.
- Cerrar cada fase con validaciones, pruebas y revisión de uso.

## Fases

1. **Base operativa — completada:** socios, planes, cuotas, cobros, comprobantes, ficha financiera y validaciones.
2. **Morosidad y renovaciones — completada:** prioridades por atraso, deuda consolidada, contacto, seguimiento y acceso directo al cobro.
3. **Reportes — completada:** ingresos, medios de pago, altas, renovaciones y evolución mensual.
4. **Caja — actual:** apertura, movimientos, cierre y arqueo por usuario y jornada.
5. **Stock de suplementos:** productos, entradas, ventas, ajustes y alertas de mínimo.
6. **Preparación final:** permisos, auditoría, copias de seguridad, revisión integral y puesta en producción.

## Criterio de cierre de la fase actual

- Apertura individual por usuario con fondo inicial y una sola caja activa.
- Cobros y anulaciones de todos los medios integrados automáticamente.
- Ingresos y egresos manuales con concepto, fecha y responsable.
- Efectivo esperado separado de transferencias y tarjetas.
- Cierre con efectivo contado, diferencia y observaciones.
- Historial por usuario y vista general para administración.

**Estado de validación:** Reportes está completado. Caja tiene implementación y pruebas automatizadas completadas; queda su revisión visual autenticada antes de iniciar Stock.
