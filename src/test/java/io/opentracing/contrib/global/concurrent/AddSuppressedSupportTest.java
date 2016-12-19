package io.opentracing.contrib.global.concurrent;

import org.junit.Test;

import static io.opentracing.contrib.global.concurrent.AddSuppressedSupport.addSuppressedOrLog;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * @author Sjoerd Talsma
 */
public class AddSuppressedSupportTest {

    /**
     * Tests exception combinations.
     * Main exception should always be returned if not-null.
     * Otherwise the suppressed exception should be returned.
     */
    @Test
    public void testExceptionCombinations() {
        final Exception mainException = new Exception(), suppressedException = new Exception();
        assertThat(addSuppressedOrLog(null, null, null), is(nullValue()));
        assertThat(addSuppressedOrLog(mainException, null, "message"), is(sameInstance(mainException)));
        assertThat(addSuppressedOrLog(null, suppressedException, "message"), is(sameInstance(suppressedException)));
        assertThat(addSuppressedOrLog(mainException, suppressedException, "message"), is(sameInstance(mainException)));
    }

}
