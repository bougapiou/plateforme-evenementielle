import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../data/domain.dart';

class ScannerScreen extends ConsumerStatefulWidget {
  final String eventId;
  final String eventNom;
  const ScannerScreen({
    super.key,
    required this.eventId,
    required this.eventNom,
  });

  @override
  ConsumerState<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends ConsumerState<ScannerScreen> {
  final _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
  );
  bool _processing = false;
  ScanOutcome? _outcome;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_processing || _outcome != null) return;
    final raw = capture.barcodes
        .map((b) => b.rawValue)
        .firstWhere((v) => v != null && v.isNotEmpty, orElse: () => null);
    if (raw == null) return;

    setState(() {
      _processing = true;
      _error = null;
    });
    await _controller.stop();
    try {
      final res = await ref
          .read(checkinRepositoryProvider)
          .scan(token: raw, eventId: widget.eventId);
      setState(() => _outcome = res);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _processing = false);
    }
  }

  Future<void> _next() async {
    setState(() {
      _outcome = null;
      _error = null;
    });
    await _controller.start();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.eventNom, overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            icon: const Icon(Icons.flash_on),
            onPressed: () => _controller.toggleTorch(),
          ),
          IconButton(
            icon: const Icon(Icons.cameraswitch),
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Stack(
        children: [
          MobileScanner(controller: _controller, onDetect: _onDetect),
          Center(
            child: Container(
              width: 240,
              height: 240,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white70, width: 3),
                borderRadius: BorderRadius.circular(16),
              ),
            ),
          ),
          if (_processing)
            const Positioned.fill(
              child: ColoredBox(
                color: Colors.black45,
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
          if (_outcome != null || _error != null)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: _ResultPanel(
                outcome: _outcome,
                error: _error,
                onNext: _next,
              ),
            ),
        ],
      ),
    );
  }
}

class _ResultPanel extends StatelessWidget {
  final ScanOutcome? outcome;
  final String? error;
  final VoidCallback onNext;
  const _ResultPanel({this.outcome, this.error, required this.onNext});

  @override
  Widget build(BuildContext context) {
    final (color, icon, title) = _style();
    return Material(
      color: color,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Icon(icon, color: Colors.white, size: 32),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(title,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.bold)),
                ),
              ]),
              const SizedBox(height: 8),
              if (error != null)
                Text(error!, style: const TextStyle(color: Colors.white))
              else ...[
                if (outcome!.message.isNotEmpty)
                  Text(outcome!.message,
                      style: const TextStyle(color: Colors.white, fontSize: 16)),
                if (outcome!.participantNom != null)
                  Text('Participant : ${outcome!.participantNom}',
                      style: const TextStyle(color: Colors.white)),
                if (outcome!.categorieNom != null)
                  Text('Catégorie : ${outcome!.categorieNom}',
                      style: const TextStyle(color: Colors.white)),
                if (outcome!.numeroBillet != null)
                  Text('Billet : ${outcome!.numeroBillet}',
                      style: const TextStyle(color: Colors.white)),
                if (outcome!.premierControleLe != null)
                  Text(
                      'Déjà scanné le ${Fmt.dateTime(outcome!.premierControleLe)}',
                      style: const TextStyle(color: Colors.white)),
              ],
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: Colors.black,
                  ),
                  onPressed: onNext,
                  child: const Text('Scanner le billet suivant'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  (Color, IconData, String) _style() {
    if (error != null) {
      return (const Color(0xFF991B1B), Icons.error_outline, 'Erreur');
    }
    switch (outcome!.resultat) {
      case 'VALIDE':
        return (const Color(0xFF15803D), Icons.check_circle, 'VALIDE');
      case 'DEJA_UTILISE':
        return (const Color(0xFFB45309), Icons.history, 'DÉJÀ UTILISÉ');
      default:
        return (const Color(0xFF991B1B), Icons.block, 'INVALIDE');
    }
  }
}
