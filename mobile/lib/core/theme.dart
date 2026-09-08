import 'package:flutter/material.dart';
import 'brand.dart';

/// App theme aligned with the web app: institutional blue, slate neutrals,
/// bordered white cards on an off-white ground, compact rounded controls.
final ThemeData appTheme = _build();

ThemeData _build() {
  final scheme = ColorScheme.fromSeed(
    seedColor: Brand.b600,
    brightness: Brightness.light,
  ).copyWith(
    primary: Brand.b600,
    surface: Colors.white,
    surfaceContainerHighest: Brand.s100,
    onSurface: Brand.s800,
    onSurfaceVariant: Brand.s500,
    outline: Brand.s300,
    outlineVariant: Brand.s200,
  );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: Brand.s50,
    fontFamily: null,
    textTheme: const TextTheme(
      headlineSmall: TextStyle(fontWeight: FontWeight.w700, color: Brand.s900),
      titleLarge: TextStyle(fontWeight: FontWeight.w700, color: Brand.s800),
      titleMedium: TextStyle(fontWeight: FontWeight.w600, color: Brand.s800),
      titleSmall: TextStyle(fontWeight: FontWeight.w600, color: Brand.s800),
      bodyMedium: TextStyle(color: Brand.s700),
      bodySmall: TextStyle(color: Brand.s500),
      labelSmall: TextStyle(color: Brand.s400, fontWeight: FontWeight.w600),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Brand.s900,
      elevation: 0,
      scrolledUnderElevation: 0.5,
      centerTitle: false,
      titleTextStyle: TextStyle(
        color: Brand.s900,
        fontSize: 18,
        fontWeight: FontWeight.w700,
      ),
      shape: Border(bottom: BorderSide(color: Brand.s200)),
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: const BorderSide(color: Brand.s200),
      ),
    ),
    dividerTheme: const DividerThemeData(color: Brand.s200, thickness: 1),
    chipTheme: const ChipThemeData(
      backgroundColor: Brand.s100,
      side: BorderSide.none,
      labelStyle: TextStyle(color: Brand.s700, fontSize: 12),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      elevation: 3,
      height: 64,
      indicatorColor: Brand.b50,
      labelTextStyle: WidgetStateProperty.resolveWith((s) => TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w600,
            color: s.contains(WidgetState.selected) ? Brand.b700 : Brand.s500,
          )),
      iconTheme: WidgetStateProperty.resolveWith((s) => IconThemeData(
            color: s.contains(WidgetState.selected) ? Brand.b700 : Brand.s500,
          )),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Brand.s300),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Brand.s300),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Brand.b600, width: 1.6),
      ),
      labelStyle: const TextStyle(color: Brand.s500),
      hintStyle: const TextStyle(color: Brand.s400),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: Brand.b600,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(48),
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Brand.b700,
        minimumSize: const Size.fromHeight(46),
        side: const BorderSide(color: Brand.s300),
        textStyle: const TextStyle(fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: Brand.b700),
    ),
    listTileTheme: const ListTileThemeData(
      iconColor: Brand.s500,
      titleTextStyle: TextStyle(
          color: Brand.s800, fontWeight: FontWeight.w600, fontSize: 15),
      subtitleTextStyle: TextStyle(color: Brand.s500, fontSize: 13),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: Brand.s900,
      contentTextStyle: const TextStyle(color: Colors.white),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
  );
}
