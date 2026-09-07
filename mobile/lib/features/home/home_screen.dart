import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).valueOrNull;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Événements'),
        actions: [
          if (user == null)
            TextButton(
              onPressed: () => context.push('/connexion'),
              child: const Text('Connexion'),
            )
          else
            TextButton(
              onPressed: () => context.push('/tableau-de-bord'),
              child: const Text('Mon espace'),
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Grands événements du Burkina Faso',
                      style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 8),
                  const Text(
                    'Consultez les événements, inscrivez-vous, achetez vos billets '
                    'et réservez vos stands — SIAO, FESPACO, Semaine du Numérique…',
                  ),
                  const SizedBox(height: 16),
                  if (user == null)
                    FilledButton(
                      onPressed: () => context.push('/inscription'),
                      child: const Text('Créer un compte'),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          const Card(
            child: ListTile(
              title: Text('Catalogue des événements'),
              subtitle: Text(
                'La liste publique des événements sera disponible avec le module Événements.',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
