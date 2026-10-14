package dev.firmages.core.perf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassFileAccessTest {
    enum Sample { A, B; static final long BIG = 1L << 40; static final double D = 0.5; }

    private static InputStream bytesOf(Class<?> c) {
        return c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class");
    }

    @Test
    void readsTheFlagsJavaReports() throws IOException {
        // long and double constants (two pool slots), strings, method handles of the enum's lambdas
        for (Class<?> c : new Class<?>[] {Sample.class, ClassFileAccess.class, ClassFileAccessTest.class,
                RegistryScanScope.class, String.class, Thread.State.class}) {
            try (InputStream in = c == String.class || c == Thread.State.class
                    ? Object.class.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class") : bytesOf(c)) {
                int flags = ClassFileAccess.accessFlags(in);
                assertEquals(c.isEnum(), (flags & ClassFileAccess.ACC_ENUM) != 0, c.getName());
                assertEquals(c.isInterface(), (flags & 0x0200) != 0, c.getName());
            }
        }
    }

    @Test
    void enumOrNot() throws IOException {
        try (InputStream in = bytesOf(Sample.class)) { assertTrue(ClassFileAccess.isEnum(in)); }
        try (InputStream in = bytesOf(ClassFileAccess.class)) { assertFalse(ClassFileAccess.isEnum(in)); }
    }

    @Test
    void rejectsWhatIsNoClassFile() {
        assertThrows(IOException.class, () -> ClassFileAccess.accessFlags(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5})));
        assertThrows(IOException.class, () -> ClassFileAccess.accessFlags(new ByteArrayInputStream(new byte[0])));
    }
}
