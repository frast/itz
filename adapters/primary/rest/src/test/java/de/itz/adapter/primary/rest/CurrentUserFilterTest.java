package de.itz.adapter.primary.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jboss.resteasy.spi.util.PickConstructor;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@EnableAutoWeld
@AddBeanClasses({CurrentUserFilter.class, RequestCurrentUserContext.class})
class CurrentUserFilterTest {
    @Inject
    private CurrentUserFilter filter;

    @Test
    void supportsResteasyConstructorSelectionAndCdiConstruction() {
        // RESTEasy selects this constructor before its CDI factory resolves the bean.
        assertNotNull(PickConstructor.pickSingletonConstructor(CurrentUserFilter.class));
        // Successful CDI creation also proves the fail-fast no-arg constructor was not invoked.
        assertNotNull(filter);
    }
}
