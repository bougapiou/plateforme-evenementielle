import 'package:flutter/material.dart';
import 'brand.dart';

/// Thème aligné sur la Charte graphique de l'administration burkinabè v2.0
/// et sur le site web : vert d'action, neutres chauds, Archivo (titres) +
/// Inter (texte), rayons 6 px (contrôles) / 10 px (cartes), cibles ≥ 44 px.
final ThemeData appTheme = _build();

const _titre = TextStyle(fontFamily: Brand.display, color: Brand.s900, letterSpacing: -0.3);

ThemeData _build() {
  final scheme = ColorScheme.fromSeed(
    seedColor: Brand.b600,
    brightness: Brightness.light,
  ).copyWith(
    primary: Brand.b600,
    onPrimary: Colors.white,
    primaryContainer: Brand.b50,
    onPrimaryContainer: Brand.b800,
    secondary: Brand.s800,
    onSecondary: Colors.white,
    tertiary: Brand.bleu500,
    error: Brand.r700,
    errorContainer: Brand.r50,
    onErrorContainer: Brand.r700,
    surface: Colors.white,
    surfaceTint: Colors.transparent,
    surfaceContainerLowest: Colors.white,
    surfaceContainerLow: Brand.s25,
    surfaceContainer: Brand.s50,
    surfaceContainerHigh: Brand.s100,
    surfaceContainerHighest: Brand.s100,
    onSurface: Brand.s900,
    onSurfaceVariant: Brand.s600,
    outline: Brand.s400,
    outlineVariant: Brand.s200,
    inverseSurface: Brand.s900,
    onInverseSurface: Colors.white,
  );

  final controlShape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(6));

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: Brand.s25,
    fontFamily: Brand.body,
    splashFactory: InkSparkle.splashFactory,
    textTheme: TextTheme(
      displaySmall: _titre.copyWith(fontSize: 34, fontWeight: FontWeight.w800, height: 1.15),
      headlineLarge: _titre.copyWith(fontSize: 30, fontWeight: FontWeight.w800, height: 1.15),
      headlineMedium: _titre.copyWith(fontSize: 26, fontWeight: FontWeight.w800, height: 1.2),
      headlineSmall: _titre.copyWith(fontSize: 22, fontWeight: FontWeight.w700, height: 1.25),
      titleLarge: _titre.copyWith(fontSize: 19, fontWeight: FontWeight.w700, height: 1.25),
      titleMedium: _titre.copyWith(fontSize: 16, fontWeight: FontWeight.w600, height: 1.3, letterSpacing: -0.1),
      titleSmall: _titre.copyWith(fontSize: 14, fontWeight: FontWeight.w600, letterSpacing: 0),
      bodyLarge: const TextStyle(fontSize: 16, height: 1.6, color: Brand.s800),
      bodyMedium: const TextStyle(fontSize: 15, height: 1.55, color: Brand.s700),
      bodySmall: const TextStyle(fontSize: 13, height: 1.45, color: Brand.s600),
      labelLarge: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600, fontSize: 15),
      labelMedium: const TextStyle(fontWeight: FontWeight.w500, color: Brand.s700),
      labelSmall: const TextStyle(
          fontFamily: Brand.display, fontSize: 11, color: Brand.s600, fontWeight: FontWeight.w700, letterSpacing: 0.9),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Brand.s900,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: TextStyle(
        fontFamily: Brand.display,
        color: Brand.s900,
        fontSize: 18,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
      shape: Border(bottom: BorderSide(color: Brand.s200, width: 1)),
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      margin: EdgeInsets.zero,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
        side: const BorderSide(color: Brand.s200),
      ),
    ),
    dividerTheme: const DividerThemeData(color: Brand.s200, thickness: 1, space: 1),
    chipTheme: ChipThemeData(
      backgroundColor: Colors.white,
      selectedColor: Brand.b600,
      side: const BorderSide(color: Brand.s300),
      shape: const StadiumBorder(),
      showCheckmark: false,
      labelStyle: const TextStyle(color: Brand.s700, fontSize: 13, fontWeight: FontWeight.w500),
      secondaryLabelStyle: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      shadowColor: Brand.s900.withValues(alpha: .08),
      elevation: 8,
      height: 68,
      indicatorColor: Brand.b50,
      indicatorShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
      labelTextStyle: WidgetStateProperty.resolveWith((s) => TextStyle(
            fontFamily: Brand.display,
            fontSize: 11.5,
            fontWeight: s.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
            color: s.contains(WidgetState.selected) ? Brand.b700 : Brand.s600,
          )),
      iconTheme: WidgetStateProperty.resolveWith((s) => IconThemeData(
            size: 24,
            color: s.contains(WidgetState.selected) ? Brand.b700 : Brand.s600,
          )),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      isDense: false,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: const BorderSide(color: Brand.s400),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: const BorderSide(color: Brand.s400),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: const BorderSide(color: Brand.b700, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: const BorderSide(color: Brand.r700),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(6),
        borderSide: const BorderSide(color: Brand.r700, width: 2),
      ),
      labelStyle: const TextStyle(color: Brand.s600),
      floatingLabelStyle: const TextStyle(color: Brand.b700, fontWeight: FontWeight.w600),
      hintStyle: const TextStyle(color: Brand.s500),
      errorStyle: const TextStyle(color: Brand.r700),
      prefixIconColor: Brand.s500,
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: Brand.b600,
      foregroundColor: Colors.white,
      elevation: 2,
      extendedTextStyle: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w700, fontSize: 15),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: Brand.b600,
        foregroundColor: Colors.white,
        disabledBackgroundColor: Brand.s200,
        disabledForegroundColor: Brand.s500,
        minimumSize: const Size(64, 50),
        padding: const EdgeInsets.symmetric(horizontal: 20),
        textStyle: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600, fontSize: 15),
        shape: controlShape,
      ).copyWith(
        overlayColor: WidgetStatePropertyAll(Brand.b800.withValues(alpha: .18)),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: Brand.b600,
        foregroundColor: Colors.white,
        elevation: 0,
        minimumSize: const Size(64, 50),
        textStyle: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600, fontSize: 15),
        shape: controlShape,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Brand.b700,
        minimumSize: const Size(64, 48),
        padding: const EdgeInsets.symmetric(horizontal: 18),
        side: const BorderSide(color: Brand.b600),
        textStyle: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600, fontSize: 15),
        shape: controlShape,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: Brand.b700,
        minimumSize: const Size(44, 44),
        textStyle: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600),
        shape: controlShape,
      ),
    ),
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: SegmentedButton.styleFrom(
        selectedBackgroundColor: Brand.b50,
        selectedForegroundColor: Brand.b800,
        side: const BorderSide(color: Brand.s300),
        textStyle: const TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w600),
      ),
    ),
    tabBarTheme: const TabBarThemeData(
      labelColor: Brand.b700,
      unselectedLabelColor: Brand.s600,
      indicatorColor: Brand.b600,
      dividerColor: Brand.s200,
      labelStyle: TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w700, fontSize: 14),
      unselectedLabelStyle: TextStyle(fontFamily: Brand.display, fontWeight: FontWeight.w500, fontSize: 14),
    ),
    listTileTheme: const ListTileThemeData(
      iconColor: Brand.s600,
      minVerticalPadding: 10,
      titleTextStyle: TextStyle(color: Brand.s900, fontWeight: FontWeight.w600, fontSize: 15),
      subtitleTextStyle: TextStyle(color: Brand.s600, fontSize: 13),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(color: Brand.b600, linearTrackColor: Brand.s100),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      titleTextStyle: _titre.copyWith(fontSize: 20, fontWeight: FontWeight.w700),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: Brand.s900,
      contentTextStyle: const TextStyle(fontFamily: Brand.body, color: Colors.white, fontSize: 14),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
    ),
  );
}
