package com.kuet.clubhub.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Reads the fingerprint of the key this build was signed with. Firebase Google sign-in only works
 * when exactly this SHA-1 / SHA-256 is registered for the app in the Firebase console, so the
 * login screen shows these values when Firebase reports a certificate hash error.
 */
public final class SigningCertificate {

    private SigningCertificate() {
    }

    /** Returns e.g. "AB:CD:..." for {@code "SHA-1"} or {@code "SHA-256"}, or null if unreadable. */
    @Nullable
    @SuppressWarnings("deprecation")
    public static String fingerprint(Context context, String algorithm) {
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo signing = info.signingInfo;
                if (signing == null) {
                    return null;
                }
                signatures = signing.hasMultipleSigners()
                        ? signing.getApkContentsSigners()
                        : signing.getSigningCertificateHistory();
            } else {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                        PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) {
                return null;
            }
            byte[] digest = MessageDigest.getInstance(algorithm).digest(signatures[0].toByteArray());
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) {
                    out.append(':');
                }
                out.append(String.format(Locale.ROOT, "%02X", digest[i]));
            }
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
