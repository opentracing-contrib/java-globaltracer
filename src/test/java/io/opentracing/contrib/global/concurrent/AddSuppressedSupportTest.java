package io.opentracing.contrib.global.concurrent;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * @author Sjoerd Talsma
 */
public class AddSuppressedSupportTest {

    @Test
    public void testExceptionCombinations() {
        final Exception ex1 = new Exception(), ex2 = new Exception();
        assertThat(AddSuppressedSupport.addSuppressedOrLog(null, null, null), is(nullValue()));
        assertThat(AddSuppressedSupport.addSuppressedOrLog(ex1, null, "message"), is(sameInstance(ex1)));
        assertThat(AddSuppressedSupport.addSuppressedOrLog(null, ex1, "message"), is(sameInstance(ex1)));
        assertThat(AddSuppressedSupport.addSuppressedOrLog(null, ex1, "message"), is(sameInstance(ex1)));
        assertThat(AddSuppressedSupport.addSuppressedOrLog(ex1, ex2, "message"), is(sameInstance(ex1)));
    }

}
