package dev.firmages.core.perf;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the access flags of a class file without defining the class (SPEC §20, enums only). ASM is not visible from
 * the game layer, so the header is walked by hand: magic, version, the constant pool (JVMS §4.4), then
 * {@code access_flags}.
 */
public final class ClassFileAccess {
    public static final int ACC_ENUM = 0x4000;

    private ClassFileAccess() {}

    /** The {@code access_flags} of the class file in {@code in}; IOException when it is not a class file. */
    public static int accessFlags(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        if (d.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
        d.readUnsignedShort(); // minor
        d.readUnsignedShort(); // major
        int count = d.readUnsignedShort();
        for (int i = 1; i < count; i++) {
            int tag = d.readUnsignedByte();
            switch (tag) {
                case 1 -> d.skipNBytes(d.readUnsignedShort()); // Utf8
                case 7, 8, 16, 19, 20 -> d.skipNBytes(2); // Class, String, MethodType, Module, Package
                case 15 -> d.skipNBytes(3); // MethodHandle
                case 3, 4, 9, 10, 11, 12, 17, 18 -> d.skipNBytes(4);
                case 5, 6 -> { d.skipNBytes(8); i++; } // Long, Double take two slots
                default -> throw new IOException("bad constant pool tag " + tag);
            }
        }
        return d.readUnsignedShort();
    }

    /** True when the class file in {@code in} declares an enum. */
    public static boolean isEnum(InputStream in) throws IOException {
        return (accessFlags(in) & ACC_ENUM) != 0;
    }
}
