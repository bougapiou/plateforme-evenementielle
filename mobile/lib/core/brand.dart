import 'package:flutter/material.dart';

/// Palette : vert et rouge du drapeau du Burkina Faso + étoile dorée, sur des
/// neutres (échelle « slate »). Les noms `bXX` (vert) / `sXX` (neutres) sont
/// conservés pour compat avec les écrans existants.
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

  // Neutres (slate)
  static const s50 = Color(0xFFF8FAFC);
  static const s100 = Color(0xFFF1F5F9);
  static const s200 = Color(0xFFE2E8F0);
  static const s300 = Color(0xFFCBD5E1);
  static const s400 = Color(0xFF94A3B8);
  static const s500 = Color(0xFF64748B);
  static const s700 = Color(0xFF334155);
  static const s800 = Color(0xFF1E293B);
  static const s900 = Color(0xFF0F172A);

  // Accents drapeau
  static const green = Color(0xFF0E7A3C);
  static const red = Color(0xFFD21034); // rouge Burkina
  static const gold = Color(0xFFF4C531); // étoile / or
  static const amber = Color(0xFF9A6A12);
}
