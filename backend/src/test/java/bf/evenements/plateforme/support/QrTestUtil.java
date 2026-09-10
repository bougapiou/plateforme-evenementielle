package bf.evenements.plateforme.support;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import javax.imageio.ImageIO;

/** Decodes the text payload of a QR PNG produced by the API (hardened against
 *  the occasional {@link HybridBinarizer} miss on freshly generated images). */
public final class QrTestUtil {

    private QrTestUtil() {
    }

    public static String decode(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        LuminanceSource source = new BufferedImageLuminanceSource(img);
        Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            return new MultiFormatReader()
                    .decode(new BinaryBitmap(new HybridBinarizer(source)), hints)
                    .getText();
        } catch (NotFoundException first) {
            Result r = new MultiFormatReader()
                    .decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints);
            return r.getText();
        }
    }
}
