import 'package:flutter/material.dart';

/// Palette « Faso » — inspirée du Burkina Faso : vert et rouge du drapeau,
/// étoile dorée, et neutres chauds (terre du Sahel, coton Faso Dan Fani).
///
/// Les noms `bXX` / `sXX` sont conservés (compat écrans existants) mais portent
/// désormais des valeurs chaudes : `b*` = vert Burkina, `s*` = sable → argile.
class Brand {
  Brand._();

  // Vert Burkina (drapeau ~ #009543), décliné en échelle
  static const b50 = Color(0xFFEAF6EE);
  static const b100 = Color(0xFFCDEAD7);
  static const b200 = Color(0xFF9BD5AF);
  static const b500 = Color(0xFF1C9B54);
  static const b600 = Color(0xFF0E7A3C); // primaire
  static const b700 = Color(0xFF0A5F2E);
  static const b800 = Color(0xFF07461F);

  // Neutres chauds (sable du Sahel → argile / bogolan)
  static const s50 = Color(0xFFFAF6EF); // fond application
  static const s100 = Color(0xFFF1E9DA);
  static const s200 = Color(0xFFE3D6BF); // bordures
  static const s300 = Color(0xFFCDBB9C);
  static const s400 = Color(0xFFA8916E); // texte secondaire clair
  static const s500 = Color(0xFF7A6247); // texte secondaire
  static const s700 = Color(0xFF4A3A28);
  static const s800 = Color(0xFF33281B); // titres
  static const s900 = Color(0xFF241B12); // texte fort

  // Accents drapeau
  static const green = Color(0xFF0E7A3C);
  static const red = Color(0xFFD21034); // rouge Burkina
  static const gold = Color(0xFFF4C531); // étoile / or
  static const amber = Color(0xFF9A6A12);
}
