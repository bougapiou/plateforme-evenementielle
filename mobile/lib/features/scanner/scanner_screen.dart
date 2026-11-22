import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../data/domain.dart';
import 'offline_store.dart';
import 'scan_widgets.dart';

class ScannerScreen extends ConsumerStatefulWidget {
  final String eventId;
  final String eventNom;
  final String? activityId;
  final String? activiteNom;

  /// Public slug of the event, to jump to its live attendance.
  final String? slug;

  /// Direction chosen by the controller before scanning: 'ENTREE' or 'SORTIE'.
  final String sens;
  const ScannerScreen({
    super.key,
    required this.eventId,
    required this.eventNom,
    this.activityId,
    this.activiteNom,
    this.slug,
    this.sens = 'ENTREE',
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
  late String _sens = widget.sens;
  Map<String, int> _stats = const {};
  OfflineStore? _offline;
  bool _forceOffline = false;
  bool _syncing = false;
  bool _sound = true;

  @override
  void initState() {
    super.initState();
    _refreshStats();
    if (OfflineStore.supported) {
      OfflineStore.load(widget.eventId).then((s) {
        if (!mounted) return;
        setState(() => _offline = s);
        if (s.pending > 0) _sync(silent: true);
      });
    }
  }

  /// Downloads the list of valid tickets so the scanner keeps working without network.
  Future<void> _preparePack() async {
    final store = _offline;
    if (store == null) return;
    try {
      final pack = await ref.read(checkinRepositoryProvider).offlinePack(widget.eventId);
      await store.applyPack(pack);
      if (!mounted) return;
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Mode hors ligne prêt : ${(pack['billets'] as List).length} billet(s) chargé(s).')));
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  /// Sends the offline scans; each one carries a clientRef so a replay is harmless.
  Future<void> _sync({bool silent = false}) async {
    final store = _offline;
    if (store == null || store.pending == 0 || _syncing) return;
    setState(() => _syncing = true);
    try {
      final results = await ref
          .read(checkinRepositoryProvider)
          .sync(store.queue.map((q) => q.toJson()).toList());
      await store.acknowledge(results
          .where((r) => r['resultat'] != 'ERREUR')
          .map((r) => r['clientRef'] as String? ?? ''));
      _refreshStats();
      if (!silent && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('${results.length} scan(s) synchronisé(s).')));
      }
    } on ApiException catch (e) {
      if (!silent && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => _syncing = false);
    }
  }

  /// Felt and heard answer at the door: short tap + click when valid,
  /// long buzz + alert when refused.
  void _feedback(String resultat) {
    if (resultat == 'VALIDE') {
      HapticFeedback.mediumImpact();
      if (_sound) SystemSound.play(SystemSoundType.click);
    } else {
      HapticFeedback.vibrate();
      if (_sound) SystemSound.play(SystemSoundType.alert);
    }
  }

  /// Walk-in visitor: creates a free ticket (name + phone) and lets him in.
  Future<void> _onSiteDialog() async {
    final nom = TextEditingController();
    final tel = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Inscription sur place'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(widget.activiteNom ?? 'Entrée générale', style: const TextStyle(fontWeight: FontWeight.w600)),
          TextField(controller: nom, decoration: const InputDecoration(labelText: 'Nom et prénom')),
          TextField(
              controller: tel,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: 'Téléphone')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Créer et faire entrer')),
        ],
      ),
    );
    if (ok != true) return;
    if (nom.text.trim().isEmpty || !RegExp(r'^\+?[0-9 ]{6,20}$').hasMatch(tel.text.trim())) {
      setState(() => _error = 'Renseignez le nom et un numéro de téléphone valide.');
      return;
    }
    setState(() {
      _processing = true;
      _error = null;
    });
    await _controller.stop();
    try {
      final (numero, res) = await ref.read(checkinRepositoryProvider).onSite(
          eventId: widget.eventId, activityId: widget.activityId, nom: nom.text.trim(), telephone: tel.text.trim());
      _feedback(res.resultat);
      setState(() => _outcome = ScanOutcome(
          resultat: res.resultat,
          sens: res.sens,
          message: 'Inscrit sur place — billet $numero',
          participantNom: res.participantNom,
          categorieNom: res.categorieNom,
          numeroBillet: numero,
          activiteNom: res.activiteNom));
      _refreshStats();
    } on ApiException catch (e) {
      _feedback('INVALIDE');
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _processing = false);
    }
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
    final offline = _offline;
    try {
      if (_forceOffline && offline != null && offline.hasPack) {
        final res = await offline.scan(raw,
            activityId: widget.activityId, activiteNom: widget.activiteNom, sens: _sens);
        _feedback(res.resultat);
        setState(() => _outcome = res);
        return;
      }
      final res = await ref.read(checkinRepositoryProvider).scan(
            token: raw,
            eventId: widget.eventId,
            activityId: widget.activityId,
            sens: _sens,
          );
      _feedback(res.resultat);
      setState(() => _outcome = res);
      _refreshStats();
      if ((offline?.pending ?? 0) > 0) _sync(silent: true);
    } on ApiException catch (e) {
      // Network down: fall back on the downloaded list when there is one.
      if (e.status == 0 && offline != null && offline.hasPack) {
        final res = await offline.scan(raw,
            activityId: widget.activityId, activiteNom: widget.activiteNom, sens: _sens);
        _feedback(res.resultat);
        setState(() => _outcome = res);
      } else {
        _feedback('INVALIDE');
        setState(() => _error = e.message);
      }
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
    final showResult = _outcome != null || _error != null;
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.eventNom,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 16)),
            Text(
              '${widget.activiteNom ?? 'Entrée générale'} · '
              '${_sens == 'SORTIE' ? 'contrôle sortie' : 'contrôle entrée'}',
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.normal),
            ),
          ],
        ),
        actions: [
          if (_offline != null)
            PopupMenuButton<String>(
              tooltip: 'Hors ligne et options',
              icon: Badge(
                isLabelVisible: (_offline?.pending ?? 0) > 0,
                label: Text('${_offline?.pending ?? 0}'),
                child: Icon(_forceOffline ? Icons.cloud_off : Icons.cloud_done_outlined),
              ),
              onSelected: (v) {
                switch (v) {
                  case 'pack':
                    _preparePack();
                  case 'mode':
                    setState(() => _forceOffline = !_forceOffline);
                  case 'sync':
                    _sync();
                  case 'son':
                    setState(() => _sound = !_sound);
                  case 'surplace':
                    _onSiteDialog();
                }
              },
              itemBuilder: (_) => [
                PopupMenuItem(
                    value: 'pack',
                    child: Text(_offline!.hasPack
                        ? 'Actualiser la liste hors ligne'
                        : 'Préparer le mode hors ligne')),
                if (_offline!.hasPack)
                  CheckedPopupMenuItem(
                      value: 'mode', checked: _forceOffline, child: const Text('Scanner hors ligne')),
                PopupMenuItem(
                    value: 'sync',
                    enabled: _offline!.pending > 0 && !_syncing,
                    child: Text('Synchroniser (${_offline!.pending} en attente)')),
                CheckedPopupMenuItem(value: 'son', checked: _sound, child: const Text('Signal sonore')),
                const PopupMenuItem(value: 'surplace', child: Text('Inscription sur place')),
              ],
            )
          else
            IconButton(
              icon: const Icon(Icons.person_add_alt),
              tooltip: 'Inscription sur place',
              onPressed: _onSiteDialog,
            ),
          IconButton(
            icon: const Icon(Icons.flash_on),
            tooltip: 'Lampe',
            onPressed: () => _controller.toggleTorch(),
          ),
          IconButton(
            icon: const Icon(Icons.cameraswitch),
            tooltip: 'Changer de caméra',
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Stack(
        children: [
          MobileScanner(controller: _controller, onDetect: _onDetect),
          Positioned(
            top: 12,
            left: 12,
            right: 12,
            child: Center(
              child: DirectionSwitch(
                value: _sens,
                onChanged: (v) => setState(() => _sens = v),
              ),
            ),
          ),
          if (_stats.isNotEmpty)
            Positioned(
              bottom: showResult ? null : 16,
              top: showResult ? 64 : null,
              left: 12,
              right: 12,
              child: CounterBar(
                stats: _stats,
                onPresence: widget.slug == null || widget.activityId != null
                    ? null
                    : () => context.push('/presence-en-direct/${widget.slug}'),
              ),
            ),
          Center(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              ScanFrame(
                color: _outcome == null
                    ? Colors.white
                    : (_outcome!.resultat == 'VALIDE'
                        ? const Color(0xFF4ADE80)
                        : _outcome!.resultat == 'DEJA_UTILISE'
                            ? const Color(0xFFFB923C)
                            : const Color(0xFFF87171)),
              ),
              if (!showResult)
                const Padding(
                  padding: EdgeInsets.only(top: 14),
                  child: _Hint('Placez le QR du billet dans le cadre'),
                ),
            ]),
          ),
          if (_processing)
            const Positioned.fill(
              child: ColoredBox(
                color: Colors.black45,
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
          if (showResult)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: ScanResultPanel(
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

class _Hint extends StatelessWidget {
  final String text;
  const _Hint(this.text);

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(99),
        ),
        child: Text(text, style: const TextStyle(color: Colors.white, fontSize: 13)),
      );
}

/// Entrée / Sortie switch floating over the camera.
class DirectionSwitch extends StatelessWidget {
  final String value;
  final ValueChanged<String> onChanged;
  const DirectionSwitch({super.key, required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    Widget option(String v, String label, IconData icon, Color color) {
      final on = value == v;
      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => onChanged(v),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          decoration: BoxDecoration(
            color: on ? color : Colors.transparent,
            borderRadius: BorderRadius.circular(99),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 18, color: on ? Colors.white : Colors.white70),
            const SizedBox(width: 6),
            Text(label,
                style: TextStyle(
                    fontWeight: FontWeight.w700,
                    color: on ? Colors.white : Colors.white70)),
          ]),
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(99),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        option('ENTREE', 'Entrée', Icons.login, const Color(0xFF16A34A)),
        option('SORTIE', 'Sortie', Icons.logout, const Color(0xFF475569)),
      ]),
    );
  }
}

/// The verdict of a scan: colour, big icon, who it is, and what to do next.
class ScanResultPanel extends StatelessWidget {
  final ScanOutcome? outcome;
  final String? error;
  final VoidCallback onNext;
  const ScanResultPanel({super.key, this.outcome, this.error, required this.onNext});

  @override
  Widget build(BuildContext context) {
    final (color, icon, title) = _style();
    final o = outcome;
    final rows = <(IconData, String)>[
      if (o?.categorieNom != null) (Icons.confirmation_number_outlined, o!.categorieNom!),
      if (o?.activiteNom != null) (Icons.event_note_outlined, o!.activiteNom!),
      if (o?.numeroBillet != null) (Icons.tag, 'Billet ${o!.numeroBillet}'),
      if (o?.premierControleLe != null)
        (Icons.history, 'Déjà scanné le ${Fmt.dateTime(o!.premierControleLe)}'),
    ];
    return Material(
      color: color,
      elevation: 12,
      borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(children: [
                Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: .2),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(icon, color: Colors.white, size: 30),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(title,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          letterSpacing: .3,
                          fontWeight: FontWeight.w800)),
                ),
              ]),
              const SizedBox(height: 12),
              if (error != null)
                Text(error!, style: const TextStyle(color: Colors.white, fontSize: 15))
              else ...[
                if (o!.participantNom != null)
                  Text(o.participantNom!,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700)),
                if (o.message.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(o.message,
                        style: const TextStyle(color: Colors.white, fontSize: 15)),
                  ),
                if (rows.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: .14),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      children: [
                        for (int i = 0; i < rows.length; i++) ...[
                          if (i > 0) const SizedBox(height: 6),
                          Row(children: [
                            Icon(rows[i].$1, size: 16, color: Colors.white70),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(rows[i].$2,
                                  style: const TextStyle(color: Colors.white, fontSize: 14)),
                            ),
                          ]),
                        ],
                      ],
                    ),
                  ),
                ],
              ],
              const SizedBox(height: 14),
              FilledButton.icon(
                style: FilledButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: color,
                ),
                onPressed: onNext,
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('Scanner le billet suivant'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  (Color, IconData, String) _style() {
    if (error != null) {
      return (const Color(0xFF991B1B), Icons.error_outline, 'ERREUR');
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

/// Live counters over the camera: entries, exits, present, re-entries (ticket scans).
class CounterBar extends StatelessWidget {
  final Map<String, int> stats;

  /// Opens the event's live attendance (tickets + sensors); hidden when null.
  final VoidCallback? onPresence;
  const CounterBar({super.key, required this.stats, this.onPresence});

  @override
  Widget build(BuildContext context) {
    final cells = [
      ('Entrées', stats['entrees'] ?? 0, Icons.login, const Color(0xFF86EFAC)),
      ('Sorties', stats['sorties'] ?? 0, Icons.logout, const Color(0xFFCBD5E1)),
      ('Présents', stats['presents'] ?? 0, Icons.groups, const Color(0xFF7DD3FC)),
      ('Ré-entrées', stats['reentrees'] ?? 0, Icons.replay, const Color(0xFFFCD34D)),
    ];
    return Material(
      color: const Color(0xD90F172A),
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
        child: Row(children: [
          Expanded(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                for (final (label, value, icon, color) in cells)
                  Column(mainAxisSize: MainAxisSize.min, children: [
                    Icon(icon, size: 16, color: color),
                    const SizedBox(height: 2),
                    Text('$value',
                        style: TextStyle(
                            fontWeight: FontWeight.w800, color: color, fontSize: 18)),
                    Text(label,
                        style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8))),
                  ]),
              ],
            ),
          ),
          if (onPresence != null)
            IconButton(
              tooltip: 'Présence en direct (billets + capteurs)',
              onPressed: onPresence,
              icon: const Icon(Icons.open_in_full, color: Colors.white, size: 20),
            ),
        ]),
      ),
    );
  }
}
