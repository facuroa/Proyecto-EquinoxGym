package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class BrandingModelAdvice {

    private final String gymName;

    public BrandingModelAdvice(@Value("${equinox.branding.gym-name:Gym System}") String gymName) {
        this.gymName = gymName;
    }

    @ModelAttribute("gymName")
    public String gymName() {
        return gymName;
    }
}
