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

/// Armoiries du Burkina Faso (fichier de la charte graphique).
/// Taille minimale : 32 px ; 44 px en en-tête étroit ; 56 px en en-tête.
class Armoiries extends StatelessWidget {
  final double height;
  const Armoiries({super.key, this.height = 44});

  @override
  Widget build(BuildContext context) => Image.asset(
        'assets/armoiries.png',
        height: height,
        fit: BoxFit.contain,
        filterQuality: FilterQuality.medium,
        errorBuilder: (_, __, ___) => FasoStar(size: height * 0.7),
      );
}

/// Bloc-marque officiel : armoiries + « BURKINA FASO » + devise + nom du service.
class FasoBrandBlock extends StatelessWidget {
  final double emblem;
  final bool devise;
  final bool service;
  final bool onDark;
  const FasoBrandBlock({
    super.key,
    this.emblem = 40,
    this.devise = true,
    this.service = true,
    this.onDark = false,
  });

  @override
  Widget build(BuildContext context) {
    final fg = onDark ? Colors.white : Brand.s900;
    final soft = onDark ? Brand.s300 : Brand.s600;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: onDark ? const EdgeInsets.all(3) : EdgeInsets.zero,
          decoration: onDark
              ? BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6))
              : null,
          child: Armoiries(height: emblem),
        ),
        const SizedBox(width: 10),
        Flexible(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('BURKINA FASO',
                  style: TextStyle(
                      fontFamily: Brand.display,
                      fontWeight: FontWeight.w800,
                      fontSize: 14,
                      letterSpacing: 0.6,
                      color: fg)),
              if (devise)
                Text('La Patrie ou la Mort, nous Vaincrons',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 10.5, fontStyle: FontStyle.italic, color: soft)),
            ],
          ),
        ),
        // Co-marquage (charte) : armoiries d'abord, filet vertical, puis la marque du service.
        if (service) ...[
          const SizedBox(width: 10),
          Container(width: 1, height: emblem * 0.8, color: onDark ? Brand.s600 : Brand.s300),
          const SizedBox(width: 10),
          Container(
            padding: onDark ? const EdgeInsets.symmetric(horizontal: 6, vertical: 4) : EdgeInsets.zero,
            decoration: onDark
                ? BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6))
                : null,
            child: Image.asset('assets/logo_mark.png',
                height: emblem * 0.62,
                fit: BoxFit.contain,
                semanticLabel: 'PNE — Plateforme nationale des événements'),
          ),
        ],
      ],
    );
  }
}

/// Filet national : rouge au-dessus, vert au-dessous, étoile d'or centrée.
/// Une seule fois, sous l'en-tête officiel — jamais comme séparateur décoratif.
class FasoFilet extends StatelessWidget {
  final double height;
  const FasoFilet({super.key, this.height = 10});

  @override
  Widget build(BuildContext context) => SizedBox(
        height: height,
        child: Stack(
          alignment: Alignment.center,
          children: [
            Column(children: [
              Expanded(child: Container(color: Brand.red)),
              Expanded(child: Container(color: Brand.green)),
            ]),
            FasoStar(size: height * 0.95),
          ],
        ),
      );
}

/// Repère national : courte vignette rouge/vert + étoile, devant un surtitre.
class FasoRepere extends StatelessWidget {
  const FasoRepere({super.key});

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(2),
        child: SizedBox(
          width: 22,
          height: 10,
          child: Stack(alignment: Alignment.center, children: [
            Column(children: [
              Expanded(child: Container(color: Brand.red)),
              Expanded(child: Container(color: Brand.green)),
            ]),
            const FasoStar(size: 8),
          ]),
        ),
      );
}

/// Motif tissé inspiré du Faso Dan Fani (4 à 8 px, jamais derrière du texte).
class FasoTissage extends StatelessWidget {
  final double height;
  final Color fond;
  const FasoTissage({super.key, this.height = 6, this.fond = Brand.s900});

  @override
  Widget build(BuildContext context) => SizedBox(
        height: height,
        width: double.infinity,
        child: CustomPaint(painter: _TissagePainter(fond)),
      );
}

class _TissagePainter extends CustomPainter {
  final Color fond;
  _TissagePainter(this.fond);

  static const List<(Color?, double)> _fils = [
    (Color(0xFF026C31), 14.0),
    (null, 3.0),
    (Color(0xFFFFED00), 4.0),
    (null, 3.0),
    (Color(0xFFA2040E), 10.0),
    (null, 3.0),
    (Color(0xFFF7F5F2), 2.0),
    (null, 3.0),
  ];

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = fond);
    var x = 0.0;
    while (x < size.width) {
      for (final (c, w) in _fils) {
        if (c != null) canvas.drawRect(Rect.fromLTWH(x, 0, w, size.height), Paint()..color = c);
        x += w;
      }
    }
  }

  @override
  bool shouldRepaint(covariant _TissagePainter old) => old.fond != fond;
}

/// Surtitre (Archivo 700, capitales, interlettrage) avec repère national optionnel.
class Surtitre extends StatelessWidget {
  final String text;
  final Color color;
  final bool repere;
  const Surtitre(this.text, {super.key, this.color = Brand.b700, this.repere = false});

  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        if (repere) ...[const FasoRepere(), const SizedBox(width: 8)],
        Flexible(
          child: Text(text.toUpperCase(),
              style: TextStyle(
                  fontFamily: Brand.display,
                  fontWeight: FontWeight.w700,
                  fontSize: 11.5,
                  letterSpacing: 1,
                  color: color)),
        ),
      ]);
}

/// En-tête d'identité des écrans d'authentification : bloc officiel.
class FasoHeader extends StatelessWidget {
  final String? subtitle;
  const FasoHeader({super.key, this.subtitle});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Co-marquage (charte) : les armoiries viennent toujours en premier.
        const Armoiries(height: 44),
        const SizedBox(height: 6),
        const Text('BURKINA FASO',
            style: TextStyle(
                fontFamily: Brand.display,
                fontWeight: FontWeight.w800,
                fontSize: 16,
                letterSpacing: 0.8,
                color: Brand.s900)),
        const SizedBox(height: 2),
        const Text('La Patrie ou la Mort, nous Vaincrons',
            style: TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: Brand.s600)),
        const SizedBox(height: 18),
        Image.asset('assets/logo.png',
            height: 96,
            fit: BoxFit.contain,
            semanticLabel: 'PNE — Plateforme nationale des événements'),
        if (subtitle != null) ...[
          const SizedBox(height: 8),
          Text(subtitle!,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall),
        ],
      ],
    );
  }
}

/// Marque compacte pour les barres d'application : armoiries + « BURKINA FASO » | logo PNE.
class FasoMark extends StatelessWidget {
  final double height;
  const FasoMark({super.key, this.height = 24});

  @override
  Widget build(BuildContext context) => FasoBrandBlock(emblem: height * 1.5, devise: false);
}
