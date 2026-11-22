import 'package:flutter/material.dart';

/// Palette de la Charte graphique de l'administration burkinabè v2.0
/// (https://chartegraphique-21.mtdpce-test.gov.bf/fondations).
///
/// Les noms historiques `bXX` (vert d'action) et `sXX` (neutres) sont conservés
/// pour que tous les écrans existants suivent la charte sans réécriture.
///   - Vert   : action principale, succès
///   - Rouge  : danger, actions destructives uniquement
///   - Or     : relief rare (jamais de texte blanc dessus)
///   - Ocre   : alertes ; Bleu : information
///   - Neutres chauds (pas de gris bleuté)
class Brand {
  Brand._();

  // --- Vert (action) ---
  static const b50 = Color(0xFFECF7F0);
  static const b100 = Color(0xFFD3EDDD);
  static const b200 = Color(0xFFA8DBBC);
  static const b300 = Color(0xFF74C296);
  static const b400 = Color(0xFF3EA46D);
  static const b500 = Color(0xFF00843B); // vert officiel du drapeau
  static const b600 = Color(0xFF00843B); // primaire (--action)
  static const b700 = Color(0xFF026C31); // survol / texte vert sur clair
  static const b800 = Color(0xFF045527);
  static const b900 = Color(0xFF04411F);
  static const b950 = Color(0xFF032E16);

  // --- Neutres chauds ---
  static const s0 = Color(0xFFFFFFFF);
  static const s25 = Color(0xFFFCFBF9);
  static const s50 = Color(0xFFF7F5F2);
  static const s100 = Color(0xFFEFECE7);
  static const s200 = Color(0xFFE2DDD6);
  static const s300 = Color(0xFFCFC8BF);
  static const s400 = Color(0xFFA9A199);
  static const s500 = Color(0xFF756D66); // texte faible (contraste AA)
  static const s600 = Color(0xFF635C55); // texte atténué
  static const s700 = Color(0xFF4A443E);
  static const s800 = Color(0xFF332E2A);
  static const s900 = Color(0xFF211D1A); // texte principal
  static const s950 = Color(0xFF14110F);

  // --- Rouge (critique) ---
  static const r50 = Color(0xFFFFF1F2);
  static const r100 = Color(0xFFFFE0E2);
  static const r500 = Color(0xFFE30613);
  static const r700 = Color(0xFFA2040E);

  // --- Or (relief) ---
  static const o50 = Color(0xFFFFFCE5);
  static const o300 = Color(0xFFFFED00);
  static const o700 = Color(0xFF7D7100);

  // --- Ocre (alerte) ---
  static const ocre50 = Color(0xFFFDF4E3);
  static const ocre100 = Color(0xFFF9E6C2);
  static const ocre500 = Color(0xFFB07800);
  static const ocre700 = Color(0xFF7D5500);

  // --- Bleu (information) ---
  static const bleu50 = Color(0xFFEEF4F9);
  static const bleu100 = Color(0xFFD6E5F0);
  static const bleu500 = Color(0xFF2A6D9B);
  static const bleu700 = Color(0xFF173F5F);

  // --- Couleurs du drapeau (signaux, à utiliser avec parcimonie) ---
  static const green = Color(0xFF00843B);
  static const red = Color(0xFFE30613);
  static const gold = Color(0xFFFFED00);
  static const amber = Color(0xFF7D5500); // texte d'alerte (ocre 700)

  // --- Polices ---
  static const display = 'Archivo'; // titres, boutons, navigation
  static const body = 'Inter'; // texte courant, formulaires
}
