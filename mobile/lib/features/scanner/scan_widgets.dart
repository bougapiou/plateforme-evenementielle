import 'package:flutter/material.dart';

/// Viewfinder: four corner brackets over the camera image instead of a full outline.
class ScanFrame extends StatelessWidget {
  final double size;
  final Color color;
  const ScanFrame({super.key, this.size = 240, this.color = Colors.white});

  @override
  Widget build(BuildContext context) => IgnorePointer(
        child: SizedBox(
          width: size,
          height: size,
          child: CustomPaint(painter: _BracketsPainter(color)),
        ),
      );
}

class _BracketsPainter extends CustomPainter {
  final Color color;
  const _BracketsPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4
      ..strokeCap = StrokeCap.round;
    const arm = 38.0;
    const r = 16.0;
    final w = size.width;
    final h = size.height;

    Path corner(double x, double y, double dx, double dy) => Path()
      ..moveTo(x, y + dy * arm)
      ..lineTo(x, y + dy * r)
      ..quadraticBezierTo(x, y, x + dx * r, y)
      ..lineTo(x + dx * arm, y);

    canvas.drawPath(corner(0, 0, 1, 1), paint);
    canvas.drawPath(corner(w, 0, -1, 1), paint);
    canvas.drawPath(corner(0, h, 1, -1), paint);
    canvas.drawPath(corner(w, h, -1, -1), paint);
  }

  @override
  bool shouldRepaint(covariant _BracketsPainter old) => old.color != color;
}
