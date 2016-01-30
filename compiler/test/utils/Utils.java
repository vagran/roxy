/** Testing utilities. */
package utils;

import org.roxy.common.Log;

public class Utils {

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Throwable;
}

public static void
AssertThrows(Class<? extends Throwable> expectedThrowable, ThrowingRunnable runnable)
{
    try {
        runnable.run();
    } catch (Throwable e) {
        if (expectedThrowable.isInstance(e)) {
            System.out.println("Expected exception thrown: " + Log.GetStackTrace(e));
            return;
        }
        throw new AssertionError(
            String.format("Unexpected exception type thrown: expected %s thrown %s",
                          expectedThrowable.getSimpleName(), e.getClass().getSimpleName()),
            e);
    }
    throw new AssertionError(String.format("Expected %s to be thrown, but nothing was thrown",
                                           expectedThrowable.getSimpleName()));
}

}
