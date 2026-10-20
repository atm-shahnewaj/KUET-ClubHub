package com.kuet.clubhub.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.kuet.clubhub.R;
import com.kuet.clubhub.model.Certificate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Draws a certificate on a Canvas. The same drawing code fills the on-screen preview (a Bitmap)
 * and the PDF page (PdfDocument), so what the student sees is what the PDF contains.
 *
 * A pre-designed template image (border and corner ornaments) is bundled in the app; this class
 * adds the logo, the student's name, the event, the date, the certificate id and a QR code.
 * Names written in Bangla use the bundled Noto Sans Bengali font so they render the same on
 * every phone.
 */
public final class CertificateRenderer {

    /** A4 landscape in PDF points. All coordinates below use this space. */
    public static final int PAGE_WIDTH = 842;
    public static final int PAGE_HEIGHT = 595;

    private static final int GREEN = Color.parseColor("#00873F");
    private static final int GOLD = Color.parseColor("#B87800");
    private static final int INK = Color.parseColor("#1B2420");
    private static final int GREY = Color.parseColor("#55625B");

    private CertificateRenderer() {
    }

    /** Everything the drawing needs, loaded once (decoding images and fonts is the slow part). */
    public static final class Assets {
        final Bitmap template;
        final Bitmap logo;
        final Bitmap qr;
        final Typeface latinRegular;
        final Typeface latinBold;
        final Typeface bengaliRegular;
        final Typeface bengaliBold;

        private Assets(Bitmap template, Bitmap logo, Bitmap qr, Typeface latinRegular,
                       Typeface latinBold, @Nullable Typeface bengaliRegular,
                       @Nullable Typeface bengaliBold) {
            this.template = template;
            this.logo = logo;
            this.qr = qr;
            this.latinRegular = latinRegular;
            this.latinBold = latinBold;
            this.bengaliRegular = bengaliRegular == null ? latinRegular : bengaliRegular;
            this.bengaliBold = bengaliBold == null ? latinBold : bengaliBold;
        }

        public void recycle() {
            template.recycle();
            logo.recycle();
            qr.recycle();
        }
    }

    /** Call off the main thread. */
    public static Assets loadAssets(Context context, Certificate cert) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap template = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.certificate_template, options);
        Bitmap logo = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.kuet_logo, options);
        Bitmap qr = QrCodeGenerator.generate(QrPayload.forCertificate(cert.getCertId()), 360);
        Typeface bnRegular = ResourcesCompat.getFont(context, R.font.noto_sans_bengali_regular);
        Typeface bnBold = ResourcesCompat.getFont(context, R.font.noto_sans_bengali_bold);
        return new Assets(template, logo, qr,
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), bnRegular, bnBold);
    }

    // ---------------------------------------------------------------- outputs

    /** A bitmap of the certificate for the on-screen preview. */
    public static Bitmap renderBitmap(Certificate cert, Assets assets, int widthPx) {
        int heightPx = Math.round(widthPx * (float) PAGE_HEIGHT / PAGE_WIDTH);
        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(widthPx / (float) PAGE_WIDTH, heightPx / (float) PAGE_HEIGHT);
        draw(canvas, cert, assets);
        return bitmap;
    }

    /** Writes a one-page PDF and returns the file. */
    public static File writePdf(Context context, Certificate cert, Assets assets)
            throws IOException {
        File dir = new File(context.getFilesDir(), "certificates");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create the certificates folder.");
        }
        File file = new File(dir, "ClubHub-Certificate-" + cert.getCertId() + ".pdf");

        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
            PdfDocument.Page page = document.startPage(info);
            draw(page.getCanvas(), cert, assets);
            document.finishPage(page);
            try (OutputStream out = new FileOutputStream(file)) {
                document.writeTo(out);
            }
        } finally {
            document.close();
        }
        return file;
    }

    // ---------------------------------------------------------------- the drawing

    private static void draw(Canvas canvas, Certificate cert, Assets a) {
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(a.template, null, new RectF(0, 0, PAGE_WIDTH, PAGE_HEIGHT), bitmapPaint);

        float cx = PAGE_WIDTH / 2f;

        // Logo, centred at the top (the logo is taller than wide).
        float logoH = 70;
        float logoW = logoH * a.logo.getWidth() / a.logo.getHeight();
        canvas.drawBitmap(a.logo, null,
                new RectF(cx - logoW / 2, 62, cx + logoW / 2, 62 + logoH), bitmapPaint);

        drawCentered(canvas, "CERTIFICATE OF PARTICIPATION", cx, 168, 30, a.latinBold, GREEN, 640, 1);
        drawCentered(canvas, "This is to certify that", cx, 212, 15, a.latinRegular, GREY, 640, 1);

        String name = safe(cert.getName());
        boolean bangla = containsBengali(name);
        drawCentered(canvas, name, cx, 268, 40, bangla ? a.bengaliBold : a.latinBold,
                INK, 620, 1);

        Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
        rule.setColor(GOLD);
        rule.setStrokeWidth(1.2f);
        canvas.drawLine(cx - 230, 284, cx + 230, 284, rule);

        drawCentered(canvas, "has taken part in", cx, 318, 15, a.latinRegular, GREY, 640, 1);

        String title = safe(cert.getEventTitle());
        drawCentered(canvas, title, cx, 358, 26,
                containsBengali(title) ? a.bengaliBold : a.latinBold, GREEN, 620, 2);

        String club = safe(cert.getClubName());
        String date = cert.getEventDate() == null ? ""
                : new SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(cert.getEventDate());
        String line = club.isEmpty() ? date : date.isEmpty() ? "organized by " + club
                : "organized by " + club + "  •  " + date;
        drawCentered(canvas, line, cx, 428, 15, containsBengali(club) ? a.bengaliRegular
                : a.latinRegular, INK, 620, 1);

        // Bottom left: who issued it, and the id.
        drawLeft(canvas, "Issued by KUET ClubHub", 110, 480, 14, a.latinBold, INK);
        drawLeft(canvas, "Certificate ID: " + safe(cert.getCertId()), 110, 500, 12,
                a.latinRegular, GREY);
        if (cert.getIssuedAt() != null) {
            drawLeft(canvas, "Issued on "
                            + new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(cert.getIssuedAt()),
                    110, 517, 12, a.latinRegular, GREY);
        }
        drawLeft(canvas, "Verify it in the ClubHub app: Profile > Verify certificate.",
                110, 545, 10, a.latinRegular, GREY);

        // Bottom right: QR code that opens the same record in the Verify screen.
        float qrSize = 88;
        float qrLeft = PAGE_WIDTH - 110 - qrSize;
        canvas.drawBitmap(a.qr, null, new RectF(qrLeft, 440, qrLeft + qrSize, 440 + qrSize),
                bitmapPaint);
        drawCentered(canvas, "Scan to verify", qrLeft + qrSize / 2, 543, 10, a.latinRegular,
                GREY, 120, 1);
    }

    /**
     * Draws wrapped, centred text in a box of maxWidth around centerX, with the first line's
     * baseline at baselineY. If the text needs more than maxLines it is shrunk (down to 60 %
     * of textSize); if it still does not fit, it is cut with an ellipsis.
     */
    private static void drawCentered(Canvas canvas, String text, float centerX, float baselineY,
                                     float textSize, Typeface typeface, int color,
                                     int maxWidth, int maxLines) {
        if (text == null || text.isEmpty()) {
            return;
        }
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface);
        paint.setColor(color);

        float size = textSize;
        StaticLayout layout = layout(text, paint, size, maxWidth, Integer.MAX_VALUE);
        while (layout.getLineCount() > maxLines && size > textSize * 0.6f) {
            size -= 1f;
            layout = layout(text, paint, size, maxWidth, Integer.MAX_VALUE);
        }
        if (layout.getLineCount() > maxLines) {
            layout = layout(text, paint, size, maxWidth, maxLines);
        }

        canvas.save();
        canvas.translate(centerX - maxWidth / 2f, baselineY - layout.getLineBaseline(0));
        layout.draw(canvas);
        canvas.restore();
    }

    private static StaticLayout layout(String text, TextPaint paint, float size, int width,
                                       int maxLines) {
        paint.setTextSize(size);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
    }

    private static void drawLeft(Canvas canvas, String text, float x, float baselineY,
                                 float size, Typeface typeface, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface);
        paint.setTextSize(size);
        paint.setColor(color);
        canvas.drawText(text, x, baselineY, paint);
    }

    /** True if the text has at least one character from the Bengali Unicode block. */
    static boolean containsBengali(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) {
                return true;
            }
        }
        return false;
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s.trim();
    }
}
