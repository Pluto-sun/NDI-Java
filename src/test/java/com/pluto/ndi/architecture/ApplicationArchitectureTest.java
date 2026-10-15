package com.pluto.ndi.architecture;

import com.pluto.ndi.NdiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(NdiApplication.class);

    @Test
    void verifiesApplicationModuleBoundaries() {
        modules.verify();
    }
}
