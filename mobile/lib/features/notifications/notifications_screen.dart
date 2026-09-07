import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/format.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

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
            return ListView.separated(
              itemCount: paged.content.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (_, i) {
                final n = paged.content[i];
                return ListTile(
                  onTap: () => _tap(n),
                  leading: Icon(
                    n.lu
                        ? Icons.mark_email_read_outlined
                        : Icons.mark_email_unread,
                    color: n.lu ? null : Theme.of(context).colorScheme.primary,
                  ),
                  title: Text(n.titre,
                      style: TextStyle(
                          fontWeight:
                              n.lu ? FontWeight.normal : FontWeight.w600)),
                  subtitle: Text(
                    [n.contenu, Fmt.dateTime(n.createdAt)]
                        .where((s) => s.isNotEmpty)
                        .join('\n'),
                  ),
                  isThreeLine: n.contenu.isNotEmpty,
                );
              },
            );
          },
        ),
      ),
    );
  }
}
