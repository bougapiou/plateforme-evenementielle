/**
 * Thème aligné sur la Charte graphique de l'administration burkinabè v2.0
 * (https://chartegraphique-21.mtdpce-test.gov.bf/fondations).
 *
 * Les palettes Tailwind utilisées dans l'application sont remappées sur les
 * paliers officiels : tout le site suit la charte sans réécrire chaque classe.
 *   brand / green / emerald → Vert (action, succès)
 *   red / rose              → Rouge (danger, critique uniquement)
 *   amber / yellow / orange → Ocre (alertes)
 *   blue / sky / indigo     → Bleu (information)
 *   slate / gray            → Neutres chauds
 *   or                      → Or (relief exceptionnel)
 */
const vert = {
  50: '#ecf7f0', 100: '#d3eddd', 200: '#a8dbbc', 300: '#74c296', 400: '#3ea46d',
  500: '#00843b', 600: '#026c31', 700: '#045527', 800: '#04411f', 900: '#032e16', 950: '#021f0f',
};
const rouge = {
  50: '#fff1f2', 100: '#ffe0e2', 200: '#ffc5c9', 300: '#f99aa1', 400: '#f0636e',
  500: '#e30613', 600: '#c40511', 700: '#a2040e', 800: '#7d0610', 900: '#5a0810', 950: '#3d050b',
};
const or = {
  50: '#fffce5', 100: '#fff8bf', 200: '#fff287', 300: '#ffed00', 400: '#e8d400',
  500: '#c9b600', 600: '#a39300', 700: '#7d7100', 800: '#5c5300', 900: '#3d3800',
};
// Ocre et bleu : paliers 200/300/400/600/800 interpolés entre les valeurs publiées.
const ocre = {
  50: '#fdf4e3', 100: '#f9e6c2', 200: '#f2cf8a', 300: '#e0aa45', 400: '#c98f12',
  500: '#b07800', 600: '#976600', 700: '#7d5500', 800: '#644400', 900: '#4a3200', 950: '#2e1f00',
};
const bleu = {
  50: '#eef4f9', 100: '#d6e5f0', 200: '#a9c5da', 300: '#7ba5c4', 400: '#4f89b1',
  500: '#2a6d9b', 600: '#215780', 700: '#173f5f', 800: '#12324c', 900: '#0d2639', 950: '#081826',
};
const neutre = {
  0: '#ffffff', 25: '#fcfbf9', 50: '#f7f5f2', 100: '#efece7', 200: '#e2ddd6', 300: '#cfc8bf',
  400: '#a9a199', 450: '#8e867f', 500: '#837b73', 550: '#756d66', 600: '#635c55',
  700: '#4a443e', 800: '#332e2a', 900: '#211d1a', 950: '#14110f',
};

const ombre = 'rgba(33, 29, 26,';

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        brand: vert,
        vert,
        green: vert,
        emerald: vert,
        teal: vert,
        rouge,
        red: rouge,
        rose: rouge,
        or,
        ocre,
        amber: ocre,
        yellow: ocre,
        orange: ocre,
        bleu,
        blue: bleu,
        sky: bleu,
        indigo: bleu,
        purple: bleu,
        violet: bleu,
        neutre,
        slate: neutre,
        gray: neutre,
      },
      fontFamily: {
        sans: ['"Inter Variable"', 'Inter', '"Segoe UI"', 'system-ui', '-apple-system', 'sans-serif'],
        display: ['"Archivo Variable"', 'Archivo', '"Segoe UI"', 'system-ui', '-apple-system', 'sans-serif'],
      },
      // Rayons de la charte : 6 px (boutons, champs), 10 px (cartes), 16 px (modales).
      borderRadius: {
        md: '6px',
        lg: '6px',
        xl: '10px',
        '2xl': '16px',
        '3xl': '24px',
      },
      boxShadow: {
        xs: `0 1px 2px ${ombre} 0.06)`,
        sm: `0 1px 3px ${ombre} 0.08), 0 1px 2px ${ombre} 0.04)`,
        DEFAULT: `0 1px 3px ${ombre} 0.08), 0 1px 2px ${ombre} 0.04)`,
        md: `0 4px 12px ${ombre} 0.08), 0 1px 3px ${ombre} 0.05)`,
        lg: `0 12px 28px ${ombre} 0.10), 0 4px 8px ${ombre} 0.05)`,
        xl: `0 24px 56px ${ombre} 0.14), 0 8px 16px ${ombre} 0.06)`,
        inner: `inset 0 1px 2px ${ombre} 0.07)`,
      },
      maxWidth: {
        conteneur: '1200px',
        texte: '760px',
        etroit: '560px',
        mesure: '68ch',
      },
      transitionTimingFunction: {
        sortie: 'cubic-bezier(0.16, 0.84, 0.44, 1)',
      },
    },
  },
  plugins: [],
};
