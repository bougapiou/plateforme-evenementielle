import 'package:flutter/material.dart';
import 'brand.dart';
import 'config.dart';

/// Rewrites a backend media URL so its host matches the configured API host.
/// See [AppConfig.resolveHost].
String? resolveMediaUrl(String? url) => AppConfig.resolveHost(url);

/// Network image with a branded placeholder while loading and on failure.
class RemoteImage extends StatelessWidget {
  final String? url;
  final double? width;
  final double? height;
  final BoxFit fit;
  final IconData fallbackIcon;

  const RemoteImage({
    super.key,
    required this.url,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.fallbackIcon = Icons.event,
  });

  @override
  Widget build(BuildContext context) {
    final resolved = resolveMediaUrl(url);
    final placeholder = Container(
      width: width,
      height: height,
      color: Brand.s100,
      alignment: Alignment.center,
      child: Icon(fallbackIcon, color: Brand.s300, size: 32),
    );
    if (resolved == null) return placeholder;
    return Image.network(
      resolved,
      width: width,
      height: height,
      fit: fit,
      gaplessPlayback: true,
      loadingBuilder: (context, child, progress) =>
          progress == null ? child : placeholder,
      errorBuilder: (context, error, stack) => placeholder,
    );
  }
}

/// Same palette and pick rule as the PDFs (backend `EventTheme`) and the web: one color per event, everywhere.
const _cover = [
  (accent: Color(0xFF00843B), dark: Color(0xFF045527)), // vert
  (accent: Color(0xFF2A6D9B), dark: Color(0xFF173F5F)), // bleu
  (accent: Color(0xFFB07800), dark: Color(0xFF7D5500)), // ocre
  (accent: Color(0xFF026C31), dark: Color(0xFF04411F)), // vert foncé
  (accent: Color(0xFF635C55), dark: Color(0xFF332E2A)), // neutre
  (accent: Color(0xFF173F5F), dark: Color(0xFF0D2639)), // bleu nuit
  (accent: Color(0xFFA39300), dark: Color(0xFF5C5300)), // or
];

/// Java's String.hashCode() (UTF-16 code units, 32-bit), so the pick matches the backend exactly.
int _javaHash(String s) {
  var h = 0;
  for (final c in s.codeUnits) {
    h = (31 * h + c) & 0xFFFFFFFF;
  }
  return h >= 0x80000000 ? h - 0x100000000 : h;
}

/// The event's cover photo — or, when it has none (or it can't be loaded), a generated cover: a colored
/// backdrop with soft circles and fine stripes, whose color depends on the event.
class EventCover extends StatelessWidget {
  final String nom;
  final String? url;
  final double? width;
  final double? height;
  final BoxFit fit;

  const EventCover({
    super.key,
    required this.nom,
    required this.url,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
  });

  @override
  Widget build(BuildContext context) {
    final generated = _GeneratedCover(nom: nom, width: width, height: height);
    final resolved = (url == null || url!.isEmpty) ? null : resolveMediaUrl(url);
    if (resolved == null) return generated;
    return Image.network(
      resolved,
      width: width,
      height: height,
      fit: fit,
      gaplessPlayback: true,
      loadingBuilder: (context, child, progress) =>
          progress == null ? child : generated,
      errorBuilder: (context, error, stack) => generated,
    );
  }
}

class _GeneratedCover extends StatelessWidget {
  final String nom;
  final double? width;
  final double? height;
  const _GeneratedCover({required this.nom, this.width, this.height});

  @override
  Widget build(BuildContext context) {
    final p = _cover[_javaHash(nom) % _cover.length];
    return SizedBox(
      width: width,
      height: height,
      child: ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            ColoredBox(color: p.dark),
            Positioned(
              right: -40,
              top: -64,
              child: _Circle(220, p.accent.withValues(alpha: .25)),
            ),
            Positioned(
              right: -8,
              top: -32,
              child: _Circle(130, Colors.white.withValues(alpha: .10)),
            ),
            const CustomPaint(painter: _StripesPainter()),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.center,
                  colors: [Color(0x66000000), Color(0x00000000)],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Circle extends StatelessWidget {
  final double size;
  final Color color;
  const _Circle(this.size, this.color);

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      );
}

class _StripesPainter extends CustomPainter {
  const _StripesPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: .07)
      ..strokeWidth = 1;
    for (double x = -size.height; x < size.width; x += 16) {
      canvas.drawLine(Offset(x, size.height), Offset(x + size.height, 0), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
