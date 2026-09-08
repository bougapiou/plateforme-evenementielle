import 'package:flutter/material.dart';

/// Palette aligned with the web app (Tailwind `brand` + `slate` scales).
class Brand {
  Brand._();

  // brand (institutional blue)
  static const b50 = Color(0xFFEEF5FF);
  static const b100 = Color(0xFFD9E8FF);
  static const b200 = Color(0xFFBCD6FF);
  static const b500 = Color(0xFF356DF3);
  static const b600 = Color(0xFF1F4FE0);
  static const b700 = Color(0xFF1A3EC2);
  static const b800 = Color(0xFF1C369D);

  // slate (neutrals)
  static const s50 = Color(0xFFF8FAFC);
  static const s100 = Color(0xFFF1F5F9);
  static const s200 = Color(0xFFE2E8F0);
  static const s300 = Color(0xFFCBD5E1);
  static const s400 = Color(0xFF94A3B8);
  static const s500 = Color(0xFF64748B);
  static const s700 = Color(0xFF334155);
  static const s800 = Color(0xFF1E293B);
  static const s900 = Color(0xFF0F172A);

  // status accents (match StatusChip / web badges)
  static const green = Color(0xFF16A34A);
  static const amber = Color(0xFFB45309);
  static const red = Color(0xFF991B1B);
}
