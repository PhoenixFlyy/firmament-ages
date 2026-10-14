package dev.firmages.core.mixin.tfcregistryapi;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.perf.ClassFileAccess;
import dev.firmages.core.perf.TfcRegistryScan;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tfcregistryapi.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * SPEC §20. TFCRegistryAPI 1.2 [verified, javap]: {@code TFCRegistryAPI.<init>} calls seven
 * {@code *RegistryHelper.discoverRegistryEnums()}, each calls {@code Helpers.discoverRegistryEnums(Class)}, which
 * iterates {@code ModList.get().getMods()}, opens every mod file, walks every {@code .class} (except paths with
 * "mixin" or "client") and {@code Class.forName}s it. Seven full scans of ~200 jars cost ~37 s of the server boot
 * and produce ~17k FATAL/ERROR lines (client classes on the server). The mod list of that loop is narrowed to the
 * files {@link dev.firmages.core.perf.RegistryScanScope} selects. Inside those files the per-class lambda
 * ({@code lambda$discoverRegistryEnums$1}) {@code Class.forName}s every class and keeps it only when
 * {@code isEnum()}; loading a client class there costs a DISTXFORM error, mixin warnings and an early mixin apply
 * (~112 lines per boot). The class file's access flags are read first: a class without {@code ACC_ENUM} is answered
 * with {@code Object.class}, which fails {@code isEnum()} the same way, without defining the class. Both parts follow
 * {@code perf.limitTfcRegistryApiScan}. Applied only when TFCRegistryAPI is loaded.
 */
@Mixin(value = Helpers.class, remap = false)
public abstract class HelpersMixin {
    @WrapOperation(method = "discoverRegistryEnums", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/fml/ModList;getMods()Ljava/util/List;"))
    private static List<IModInfo> firmages$limitScan(ModList modList, Operation<List<IModInfo>> original) {
        return TfcRegistryScan.limit(original.call(modList));
    }

    @WrapOperation(method = "lambda$discoverRegistryEnums$1", at = @At(value = "INVOKE",
        target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"))
    private static Class<?> firmages$enumsOnly(String name, boolean initialize, ClassLoader loader,
                                               Operation<Class<?>> original) {
        if (TfcRegistryScan.enabled() && loader != null) {
            try (InputStream in = loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in != null && !ClassFileAccess.isEnum(in)) return Object.class;
            } catch (IOException | RuntimeException e) {
                // unreadable class file: let the API load it as before
            }
        }
        return original.call(name, initialize, loader);
    }
}
