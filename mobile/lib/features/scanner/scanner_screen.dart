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
  final String? activityId;
  final String? activiteNom;
  final bool controleSortie;
  const ScannerScreen({
    super.key,
    required this.eventId,
    required this.eventNom,
    this.activityId,
    this.activiteNom,
    this.controleSortie = false,
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
  String _sens = 'ENTREE';
  Map<String, int> _stats = const {};

  bool get _exitControl => widget.controleSortie;

  @override
  void initState() {
    super.initState();
    _refreshStats();
  }

  Future<void> _refreshStats() async {
    try {
      final s = await ref
          .read(checkinRepositoryProvider)
          .stats(widget.eventId, activityId: widget.activityId);
      if (mounted) setState(() => _stats = s);
    } catch (_) {
      // stats are best-effort
    }
  }

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
      final res = await ref.read(checkinRepositoryProvider).scan(
            token: raw,
            eventId: widget.eventId,
            activityId: widget.activityId,
            sens: _exitControl ? _sens : 'ENTREE',
          );
      setState(() => _outcome = res);
      _refreshStats();
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
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.eventNom,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 16)),
            Text(
              widget.activiteNom ?? 'Entrée générale',
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.normal),
            ),
          ],
        ),
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
          if (_exitControl)
            Positioned(
              top: 12,
              left: 12,
              right: 12,
              child: Center(
                child: SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(
                        value: 'ENTREE',
                        label: Text('Entrée'),
                        icon: Icon(Icons.login)),
                    ButtonSegment(
                        value: 'SORTIE',
                        label: Text('Sortie'),
                        icon: Icon(Icons.logout)),
                  ],
                  selected: {_sens},
                  onSelectionChanged: (s) => setState(() => _sens = s.first),
                  style: SegmentedButton.styleFrom(
                    backgroundColor: Colors.white,
                    selectedBackgroundColor:
                        _sens == 'SORTIE' ? Colors.blueGrey : Colors.green,
                    selectedForegroundColor: Colors.white,
                  ),
                ),
              ),
            ),
          if (_stats.isNotEmpty)
            Positioned(
              bottom: (_outcome != null || _error != null) ? null : 16,
              top: (_outcome != null || _error != null) ? 64 : null,
              left: 12,
              right: 12,
              child: _CounterBar(stats: _stats, exit: _exitControl),
            ),
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
                if (outcome!.activiteNom != null)
                  Text('Activité : ${outcome!.activiteNom}',
                      style: const TextStyle(
                          color: Colors.white, fontWeight: FontWeight.bold)),
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
    final o = outcome!;
    switch (o.resultat) {
      case 'VALIDE':
        if (o.sortie) {
          return (const Color(0xFF334155), Icons.logout, 'SORTIE ENREGISTRÉE');
        }
        return (
          const Color(0xFF15803D),
          o.reentree ? Icons.replay : Icons.check_circle,
          o.reentree ? 'RÉ-ENTRÉE' : 'VALIDE',
        );
      case 'DEJA_UTILISE':
        return (const Color(0xFFB45309), Icons.block, 'REFUSÉ');
      default:
        return (const Color(0xFF991B1B), Icons.block, 'INVALIDE');
    }
  }
}

class _CounterBar extends StatelessWidget {
  final Map<String, int> stats;
  final bool exit;
  const _CounterBar({required this.stats, required this.exit});

  @override
  Widget build(BuildContext context) {
    final cells = exit
        ? [
            ('Entrées', stats['entrees'] ?? 0, Icons.login, const Color(0xFF15803D)),
            ('Sorties', stats['sorties'] ?? 0, Icons.logout, const Color(0xFF334155)),
            ('Présents', stats['presents'] ?? 0, Icons.groups, const Color(0xFF1D4ED8)),
            ('Ré-entrées', stats['reentrees'] ?? 0, Icons.replay, const Color(0xFFB45309)),
          ]
        : [
            ('Valides', stats['valides'] ?? 0, Icons.check_circle, const Color(0xFF15803D)),
            ('Déjà scannés', stats['dejaUtilises'] ?? 0, Icons.history, const Color(0xFFB45309)),
            ('Invalides', stats['invalides'] ?? 0, Icons.block, const Color(0xFF991B1B)),
          ];
    return Material(
      color: Colors.white.withValues(alpha: 0.94),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            for (final (label, value, icon, color) in cells)
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(icon, size: 16, color: color),
                  Text('$value',
                      style: TextStyle(
                          fontWeight: FontWeight.bold, color: color, fontSize: 16)),
                  Text(label, style: const TextStyle(fontSize: 10)),
                ],
              ),
          ],
        ),
      ),
    );
  }
}
