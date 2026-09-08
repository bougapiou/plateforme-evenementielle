import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'brand.dart';

/// Motifs inspirés du Burkina Faso :
/// - [FasoStar] : étoile à 5 branches du drapeau
/// - [KassenaMotif] : frise géométrique (peintures murales kassena de Tiébélé)
/// - [FasoHeader] : en-tête d'identité (auth)

class FasoStar extends StatelessWidget {
  final double size;
  final Color color;
  const FasoStar({super.key, this.size = 16, this.color = Brand.gold});

  @override
  Widget build(BuildContext context) =>
      CustomPaint(size: Size.square(size), painter: _StarPainter(color));
}

class _StarPainter extends CustomPainter {
  final Color color;
  _StarPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final rOuter = size.width / 2;
    final rInner = rOuter * 0.42;
    final path = Path();
    for (var i = 0; i < 10; i++) {
      final r = i.isEven ? rOuter : rInner;
      final a = -math.pi / 2 + i * math.pi / 5;
      final p = Offset(c.dx + r * math.cos(a), c.dy + r * math.sin(a));
      i == 0 ? path.moveTo(p.dx, p.dy) : path.lineTo(p.dx, p.dy);
    }
    path.close();
    canvas.drawPath(path, Paint()..color = color);
  }

  @override
  bool shouldRepaint(covariant _StarPainter old) => old.color != color;
}

/// Frise de triangles alternés — séparateur de section / états vides.
class KassenaMotif extends StatelessWidget {
  final double height;
  final Color color;
  const KassenaMotif({super.key, this.height = 14, this.color = Brand.s300});

  @override
  Widget build(BuildContext context) => SizedBox(
        height: height,
        width: double.infinity,
        child: CustomPaint(painter: _KassenaPainter(color)),
      );
}

class _KassenaPainter extends CustomPainter {
  final Color color;
  _KassenaPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    final w = size.height;
    for (var x = 0.0; x < size.width; x += w) {
      final up = ((x / w).floor()).isEven;
      final path = Path();
      if (up) {
        path.moveTo(x, size.height);
        path.lineTo(x + w / 2, 0);
        path.lineTo(x + w, size.height);
      } else {
        path.moveTo(x, 0);
        path.lineTo(x + w / 2, size.height);
        path.lineTo(x + w, 0);
      }
      path.close();
      canvas.drawPath(path, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _KassenaPainter old) => old.color != color;
}

/// En-tête d'identité : étoile sur fond vert (façon drapeau) + nom + frise.
class FasoHeader extends StatelessWidget {
  final String subtitle;
  const FasoHeader({
    super.key,
    this.subtitle = 'Plateforme nationale de gestion des événements',
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 64,
          height: 64,
          decoration: BoxDecoration(
            color: Brand.b700,
            borderRadius: BorderRadius.circular(16),
          ),
          alignment: Alignment.center,
          child: const FasoStar(size: 32),
        ),
        const SizedBox(height: 12),
        Text('Burkina Événements',
            style: Theme.of(context)
                .textTheme
                .titleLarge
                ?.copyWith(color: Brand.b700)),
        const SizedBox(height: 2),
        Text(subtitle,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 12),
        const SizedBox(width: 120, child: KassenaMotif(height: 10, color: Brand.s200)),
      ],
    );
  }
}
