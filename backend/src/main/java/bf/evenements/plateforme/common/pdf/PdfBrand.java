package bf.evenements.plateforme.common.pdf;

import java.awt.Color;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * Shared color palette for every generated PDF (tickets, invoices, confirmations, badges), matching
 * the web/mobile brand colors so a document looks like it comes from the same platform.
 */
public final class PdfBrand {

    public static final Color GREEN = new Color(0x0E, 0x7A, 0x3C);
    public static final Color GREEN_DARK = new Color(0x0B, 0x60, 0x30);
    public static final Color GREEN_TINT = new Color(0xEC, 0xF8, 0xF0);
    public static final Color GOLD = new Color(0xF4, 0xC5, 0x31);
    public static final Color RED = new Color(0xD2, 0x10, 0x34);
    public static final Color SLATE_50 = new Color(0xF8, 0xFA, 0xFC);
    public static final Color SLATE_200 = new Color(0xE2, 0xE8, 0xF0);
    public static final Color SLATE_500 = new Color(0x64, 0x74, 0x8B);
    public static final Color SLATE_900 = new Color(0x0F, 0x17, 0x2A);

    private PdfBrand() {
    }

    /** The platform's signature: a thin red / gold / green stripe, used as a header and footer touch. */
    public static void tricolorBar(PDPageContentStream cs, float x, float y, float width, float height)
            throws IOException {
        float third = width / 3f;
        cs.setNonStrokingColor(RED);
        cs.addRect(x, y, third, height);
        cs.fill();
        cs.setNonStrokingColor(GOLD);
        cs.addRect(x + third, y, third, height);
        cs.fill();
        cs.setNonStrokingColor(GREEN);
        cs.addRect(x + 2 * third, y, width - 2 * third, height);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
    }
}
