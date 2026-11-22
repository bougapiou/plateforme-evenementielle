"""
Recolore le logo PNE aux couleurs nationales (Charte graphique BF v2.0), sans toucher au dessin :
  P (bleu)              -> rouge  #e30613
  N (vert)              -> or     #ffed00
  E (orange)            -> vert   #00843b      (ordre rouge – jaune – vert)
  arc : vert→orange     -> or→vert
  texte (bleu foncé)    -> encre  #211d1a (texte de la charte)
Les dégradés et les bords lissés sont conservés : on change la teinte et on
recale saturation / luminosité, pixel par pixel ; le blanc et le fond ne bougent pas.

Usage : python logo/recolorer_logo.py <entrée.png|.ico> <sortie> [--texte-sous=0.70]
  --texte-sous : fraction de la hauteur au-delà de laquelle le bleu est du TEXTE
                 (logo complet avec la mention) ; 1.0 = pas de texte (logo seul).
"""
import sys
import numpy as np
from PIL import Image

ROUGE = (0xE3, 0x06, 0x13)
ROUGE_FONCE = (0xA2, 0x04, 0x0E)
VERT = (0x00, 0x84, 0x3B)
VERT_FONCE = (0x04, 0x55, 0x27)
OR = (0xFF, 0xED, 0x00)
OR_FONCE = (0xE8, 0xC4, 0x00)
ENCRE = (0x21, 0x1D, 0x1A)


def rgb_to_hsv(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx = a.max(-1)
    mn = a.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    m = d > 1e-6
    rc = np.where(m, (mx - r) / np.where(m, d, 1), 0)
    gc = np.where(m, (mx - g) / np.where(m, d, 1), 0)
    bc = np.where(m, (mx - b) / np.where(m, d, 1), 0)
    h = np.where(r == mx, bc - gc, np.where(g == mx, 2.0 + rc - bc, 4.0 + gc - rc))
    h = (h / 6.0) % 1.0
    h = np.where(m, h, 0)
    s = np.where(mx > 1e-6, d / np.where(mx > 1e-6, mx, 1), 0)
    return h, s, mx


def recolorer(img: Image.Image, texte_sous: float) -> Image.Image:
    mode = img.mode
    rgba = np.asarray(img.convert('RGBA')).astype(np.float64)
    rgb = rgba[..., :3] / 255.0
    alpha = rgba[..., 3:4]
    h, s, v = rgb_to_hsv(rgb)
    H, W = h.shape
    deg = h * 360

    # « Quantité d'encre » : 1 au cœur d'une forme colorée, décroît vers le blanc (bords lissés).
    encre = np.clip(s / 0.80, 0, 1)
    colore = s > 0.08

    bleu = colore & (deg >= 185) & (deg <= 260)
    vert = colore & (deg >= 75) & (deg < 185)
    orange = colore & ((deg >= 15) & (deg < 75))
    rows = np.arange(H)[:, None] / H
    texte = bleu & (rows >= texte_sous)
    bleu = bleu & ~texte

    out = rgb.copy()

    def appliquer(masque, clair, fonce, vmin, vmax):
        # dégradé d'origine conservé : luminosité normalisée -> clair..fonce ; bords mélangés au blanc
        if not masque.any():
            return
        t = np.clip((v - vmin) / max(vmax - vmin, 1e-6), 0, 1)[..., None]
        c1 = np.array(clair) / 255.0
        c2 = np.array(fonce) / 255.0
        coeur = c2 + (c1 - c2) * t
        k = encre[..., None]
        couleur = coeur * k + (1 - k) * 1.0
        out[masque] = couleur[masque]

    def plage(m):
        vals = v[m & (encre > 0.9)]
        return (np.percentile(vals, 5), np.percentile(vals, 95)) if vals.size else (0, 1)

    appliquer(bleu, ROUGE, ROUGE_FONCE, *plage(bleu))
    appliquer(texte, ENCRE, ENCRE, 0, 1)

    # Vert et orange forment un continuum (l'arc passe du vert à l'orange) : on recolore en
    # interpolant or -> vert selon la teinte d'origine, pour garder un dégradé sans cassure.
    chaud = vert | orange
    if chaud.any():
        k = encre[..., None]
        def teinte(clair, fonce, m):
            vmin, vmax = plage(m)
            t = np.clip((v - vmin) / max(vmax - vmin, 1e-6), 0, 1)[..., None]
            return np.array(fonce) / 255.0 + (np.array(clair) / 255.0 - np.array(fonce) / 255.0) * t
        # Ordre rouge – jaune – vert : le N (vert d'origine) passe en OR, le E (orange) en VERT ;
        # l'arc suit la même inversion (or à gauche, vert à droite).
        c_ex_orange = teinte(VERT, VERT_FONCE, orange)
        c_ex_vert = teinte(OR, OR_FONCE, vert)
        w = np.clip((deg - 50) / (100 - 50), 0, 1)[..., None]  # 0 = ex-orange, 1 = ex-vert
        coeur = c_ex_orange * (1 - w) + c_ex_vert * w
        couleur = coeur * k + (1 - k)
        out[chaud] = couleur[chaud]

    res = np.concatenate([np.clip(out, 0, 1) * 255, alpha], axis=-1).astype(np.uint8)
    im = Image.fromarray(res, 'RGBA')
    return im if mode == 'RGBA' else im.convert(mode if mode in ('RGB', 'L') else 'RGBA')


if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    texte_sous = 1.0
    for a in sys.argv[3:]:
        if a.startswith('--texte-sous='):
            texte_sous = float(a.split('=', 1)[1])
    im = Image.open(src)
    if src.lower().endswith('.ico'):
        im = im.convert('RGBA')
    out = recolorer(im, texte_sous)
    if dst.lower().endswith('.ico'):
        out.convert('RGBA').save(dst, sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    else:
        out.save(dst)
    print('ok', dst, out.size)
