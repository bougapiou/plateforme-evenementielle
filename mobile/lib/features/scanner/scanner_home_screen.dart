import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// What the controller decided in the "prepare the control" sheet.
class _ControlChoice {
  final Activity? activity; // null = general entry
  final String sens; // ENTREE | SORTIE
  const _ControlChoice(this.activity, this.sens);
}

/// Lets entry-control staff pick the event they are checking people in for.
class ScannerHomeScreen extends ConsumerStatefulWidget {
  const ScannerHomeScreen({super.key});

  @override
  ConsumerState<ScannerHomeScreen> createState() => _ScannerHomeScreenState();
}

class _ScannerHomeScreenState extends ConsumerState<ScannerHomeScreen> {
  late Future<List<EventSummary>> _future;
  String? _openingId;

  @override
  void initState() {
    super.initState();
    _future = ref.read(checkinRepositoryProvider).myControllableEvents();
  }

  void _refresh() => setState(() =>
      _future = ref.read(checkinRepositoryProvider).myControllableEvents());

  Future<void> _openEvent(EventSummary e) async {
    setState(() => _openingId = e.id);
    List<Activity> activities = const [];
    try {
      activities = await ref.read(checkinRepositoryProvider).eventActivities(e.id);
    } catch (_) {
      // fall back to a plain event-wide scan
    }
    if (!mounted) return;
    setState(() => _openingId = null);

    final choice = await showEditorSheet<_ControlChoice>(
      context,
      (_) => _ControlSheet(event: e, activities: activities),
    );
    if (!mounted || choice == null) return; // sheet dismissed

    var url = '/scanner/${e.id}?nom=${Uri.encodeComponent(e.nom)}'
        '&sens=${choice.sens}&slug=${Uri.encodeComponent(e.slug)}';
    final a = choice.activity;
    if (a != null) {
      url += '&activityId=${a.id}&activiteNom=${Uri.encodeComponent(a.titre)}';
    }
    context.push(url);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Contrôle à l'entrée")),
      body: FutureView<List<EventSummary>>(
        future: _future,
        onRetry: _refresh,
        builder: (events) {
          if (events.isEmpty) {
            return const EmptyState(
              icon: Icons.qr_code_scanner,
              title: 'Aucun événement à contrôler',
              subtitle:
                  "Vous devez être organisateur de l'événement, y être ajouté comme personnel de contrôle, ou être administrateur ; l'événement doit être publié.",
            );
          }
          return MaxWidth(
            child: RefreshIndicator(
              onRefresh: () async => _refresh(),
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  const InfoBanner(
                    "Choisissez l'événement, puis scannez les QR codes des billets. "
                    'Le compteur de présence se met à jour à chaque scan.',
                    icon: Icons.qr_code_scanner,
                  ),
                  const SizedBox(height: 12),
                  for (final e in events)
                    _ControlCard(
                      event: e,
                      busy: _openingId == e.id,
                      onControl: () => _openEvent(e),
                      onPresence: () => context.push('/presence-en-direct/${e.slug}'),
                    ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _ControlCard extends StatelessWidget {
  final EventSummary event;
  final bool busy;
  final VoidCallback onControl;
  final VoidCallback onPresence;
  const _ControlCard({
    required this.event,
    required this.busy,
    required this.onControl,
    required this.onPresence,
  });

  @override
  Widget build(BuildContext context) {
    final place = [event.ville, event.lieu]
        .whereType<String>()
        .where((s) => s.isNotEmpty)
        .join(' · ');
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: SizedBox(
                width: 72,
                height: 72,
                child: EventCover(nom: event.nom, url: event.coverUrl),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                StatusChip(event.statut),
                const SizedBox(height: 6),
                Text(event.nom,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 2),
                Text(Fmt.rangeShort(event.dateDebut, event.dateFin),
                    style: Theme.of(context).textTheme.bodySmall),
                if (place.isNotEmpty)
                  Text(place,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall),
              ]),
            ),
          ]),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: FilledButton.icon(
                onPressed: busy ? null : onControl,
                icon: busy
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Icon(Icons.qr_code_scanner),
                label: const Text('Contrôler'),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.outlined(
              tooltip: 'Présence en direct',
              onPressed: onPresence,
              icon: const Icon(Icons.sensors),
              style: IconButton.styleFrom(
                minimumSize: const Size(48, 48),
                side: const BorderSide(color: Brand.s300),
                foregroundColor: Brand.b700,
              ),
            ),
          ]),
        ]),
      ),
    );
  }
}

/// One sheet instead of two: what is controlled (general entry or an activity) and the direction.
class _ControlSheet extends StatefulWidget {
  final EventSummary event;
  final List<Activity> activities;
  const _ControlSheet({required this.event, required this.activities});

  @override
  State<_ControlSheet> createState() => _ControlSheetState();
}

class _ControlSheetState extends State<_ControlSheet> {
  String _target = '__general__';
  String _sens = 'ENTREE';

  @override
  Widget build(BuildContext context) {
    return SheetScaffold(
      title: widget.event.nom,
      subtitle: 'Préparer le contrôle',
      icon: Icons.qr_code_scanner,
      action: FilledButton.icon(
        onPressed: () {
          final a = _target == '__general__'
              ? null
              : widget.activities.firstWhere((x) => x.id == _target);
          Navigator.pop(context, _ControlChoice(a, _sens));
        },
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('Commencer le contrôle'),
      ),
      children: [
        if (widget.activities.isNotEmpty) ...[
          const _Label('Que contrôlez-vous ?'),
          Column(children: [
            _Choice(
              icon: Icons.door_front_door_outlined,
              title: 'Entrée générale',
              subtitle: "Tout billet valable pour l'événement",
              selected: _target == '__general__',
              onTap: () => setState(() => _target = '__general__'),
            ),
            for (final a in widget.activities) ...[
              const SizedBox(height: 8),
              _Choice(
                icon: Icons.event_note_outlined,
                title: a.titre,
                subtitle: a.gratuit
                    ? 'Accès gratuit'
                    : a.payant
                        ? 'Accès payant'
                        : "Sur billet de l'événement",
                selected: _target == a.id,
                onTap: () => setState(() => _target = a.id),
              ),
            ],
          ]),
        ],
        const _Label('Sens du contrôle'),
        Row(children: [
          Expanded(
            child: _Choice(
              icon: Icons.login,
              title: 'Entrée',
              selected: _sens == 'ENTREE',
              tone: KitTone.green,
              compact: true,
              onTap: () => setState(() => _sens = 'ENTREE'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: _Choice(
              icon: Icons.logout,
              title: 'Sortie',
              selected: _sens == 'SORTIE',
              tone: KitTone.slate,
              compact: true,
              onTap: () => setState(() => _sens = 'SORTIE'),
            ),
          ),
        ]),
      ],
    );
  }
}

class _Label extends StatelessWidget {
  final String text;
  const _Label(this.text);

  @override
  Widget build(BuildContext context) => Text(
        text.toUpperCase(),
        style: Theme.of(context)
            .textTheme
            .labelSmall
            ?.copyWith(letterSpacing: .8, color: Brand.s500),
      );
}

/// Selectable tile: bordered, filled with the tone when chosen.
class _Choice extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final bool selected;
  final KitTone tone;
  final bool compact;
  final VoidCallback onTap;
  const _Choice({
    required this.icon,
    required this.title,
    required this.selected,
    required this.onTap,
    this.subtitle,
    this.tone = KitTone.green,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? tone.bg : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: selected ? tone.fg : Brand.s200, width: selected ? 1.6 : 1),
      ),
      child: InkWell(
        customBorder: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        onTap: onTap,
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: 12, vertical: compact ? 14 : 10),
          child: Row(
            mainAxisAlignment: compact ? MainAxisAlignment.center : MainAxisAlignment.start,
            children: [
              Icon(icon, color: selected ? tone.fg : Brand.s500),
              const SizedBox(width: 10),
              if (compact)
                Text(title,
                    style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: selected ? tone.fg : Brand.s700))
              else
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontWeight: FontWeight.w600, color: Brand.s800)),
                    if (subtitle != null)
                      Text(subtitle!,
                          style: const TextStyle(fontSize: 12.5, color: Brand.s500)),
                  ]),
                ),
              if (!compact)
                Icon(selected ? Icons.check_circle : Icons.circle_outlined,
                    color: selected ? tone.fg : Brand.s300),
            ],
          ),
        ),
      ),
    );
  }
}
