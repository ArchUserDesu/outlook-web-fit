package androidx.core.content.pm;

import android.content.pm.PackageInfo;
import android.os.Build;

/** Minimal compatibility method required by AndroidX WebKit feature detection. */
public final class PackageInfoCompat {
    private PackageInfoCompat() {}

    public static long getLongVersionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
        return info.versionCode;
    }
}
