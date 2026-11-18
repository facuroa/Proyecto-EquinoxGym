package com.equinox.EquinoxGym.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Las fechas de los formularios van en formato ISO (1995-05-10), el unico que
 * entiende un campo {@code <input type="date">}.
 *
 * <p>Con el formato regional que Spring usa por defecto, la fecha se escribia
 * en el formulario como "5/10/95": al editar un socio el campo de nacimiento
 * aparecia vacio y, al guardar, la fecha se borraba. Lo mismo pasaba con las
 * fechas al editar una cuota o un pago. Las fechas que se le muestran al
 * usuario se siguen formateando como dd/MM/yyyy en cada pantalla.
 */
@Configuration
public class FormatoFechasConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);
    }
}
