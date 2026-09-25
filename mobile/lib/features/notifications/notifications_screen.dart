import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// Maps a backend notification link (web dashboard paths) to a mobile route.
String? mobileRouteFor(String? lien) {
  if (lien == null || lien.isEmpty) return null;
  if (lien.contains('/billets')) return '/billets';
  if (lien.contains('/inscriptions') || lien.contains('/stands')) {
    return '/activite';
  }
  if (lien.contains('/factures')) return '/factures';
  final ev = RegExp(r'/evenements/([\w-]+)').firstMatch(lien);
  if (ev != null) return '/evenements/${ev.group(1)}';
  return null;
}

class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() =>
      _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> {
  late Future<Paged<AppNotification>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(notificationsRepositoryProvider).mine();
  }

  void _refresh() {
    setState(() => _future = ref.read(notificationsRepositoryProvider).mine());
    ref.invalidate(unreadCountProvider);
  }

  Future<void> _markAll() async {
    await ref.read(notificationsRepositoryProvider).markAllRead();
    _refresh();
  }

  Future<void> _tap(AppNotification n) async {
    if (!n.lu) {
      await ref.read(notificationsRepositoryProvider).markRead(n.id);
      _refresh();
    }
    final route = mobileRouteFor(n.lien);
    if (route != null && mounted) context.push(route);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          TextButton(
            onPressed: _markAll,
            child: const Text('Tout lire'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _refresh(),
        child: FutureView<Paged<AppNotification>>(
          future: _future,
          onRetry: _refresh,
          builder: (paged) {
            if (paged.content.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 120),
                EmptyState(
                  icon: Icons.notifications_none,
                  title: 'Aucune notification',
                ),
              ]);
            }
            return MaxWidth(
              child: ListView.builder(
                padding: const EdgeInsets.all(12),
                itemCount: paged.content.length,
                itemBuilder: (_, i) {
                  final n = paged.content[i];
                  return Card(
                    // an unread notification is tinted, a read one is plain
                    color: n.lu ? Colors.white : Brand.b50,
                    margin: const EdgeInsets.only(bottom: 8),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: () => _tap(n),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              IconBubble(
                                n.lu
                                    ? Icons.notifications_none
                                    : Icons.notifications_active_outlined,
                                tone: n.lu ? KitTone.slate : KitTone.green,
                                size: 40,
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(n.titre,
                                          style: TextStyle(
                                              fontSize: 15,
                                              color: Brand.s800,
                                              fontWeight: n.lu
                                                  ? FontWeight.w500
                                                  : FontWeight.w700)),
                                      if (n.contenu.isNotEmpty)
                                        Padding(
                                          padding: const EdgeInsets.only(top: 2),
                                          child: Text(n.contenu,
                                              style: Theme.of(context)
                                                  .textTheme
                                                  .bodyMedium),
                                        ),
                                      Padding(
                                        padding: const EdgeInsets.only(top: 4),
                                        child: Text(Fmt.dateTime(n.createdAt),
                                            style: Theme.of(context)
                                                .textTheme
                                                .bodySmall),
                                      ),
                                    ]),
                              ),
                              if (!n.lu)
                                const Padding(
                                  padding: EdgeInsets.only(top: 6, left: 6),
                                  child: CircleAvatar(
                                      radius: 5, backgroundColor: Brand.b600),
                                ),
                            ]),
                      ),
                    ),
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }
}
