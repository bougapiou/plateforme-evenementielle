import 'package:flutter/material.dart';
import '../data/domain.dart';
import 'brand.dart';
import 'faso.dart';

/// Coloured status pill used across orders / registrations / tickets.
class StatusChip extends StatelessWidget {
  final String code;
  const StatusChip(this.code, {super.key});

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = _colors(code, context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        statutLabel(code),
        style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w600),
      ),
    );
  }

  (Color, Color) _colors(String code, BuildContext context) {
    switch (code) {
      case 'PAYEE':
      case 'PAYE':
      case 'CONFIRMEE':
      case 'CONFIRME':
      case 'REUSSI':
      case 'EMISE':
      case 'PUBLIE':
      case 'INSCRIPTIONS_OUVERTES':
      case 'VERIFIEE':
      case 'APPROUVE':
        return (const Color(0xFFDCFCE7), const Color(0xFF166534));
      case 'EN_ATTENTE':
      case 'ATTENTE_PAIEMENT':
      case 'RESERVE_TEMP':
        return (const Color(0xFFFEF9C3), const Color(0xFF854D0E));
      case 'ANNULEE':
      case 'ANNULE':
      case 'REFUSEE':
      case 'ECHOUE':
      case 'EXPIREE':
      case 'EXPIRE':
      case 'SUSPENDUE':
      case 'SUSPENDU':
        return (const Color(0xFFFEE2E2), const Color(0xFF991B1B));
      case 'UTILISE':
        return (const Color(0xFFE0E7FF), const Color(0xFF3730A3));
      default:
        return (
          Theme.of(context).colorScheme.surfaceContainerHighest,
          Theme.of(context).colorScheme.onSurfaceVariant,
        );
    }
  }
}

class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final Widget? action;
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.action,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 84,
              height: 84,
              decoration: const BoxDecoration(
                color: Brand.b50,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Icon(icon, size: 38, color: Brand.b600),
            ),
            const SizedBox(height: 14),
            SizedBox(
              width: 120,
              child: KassenaMotif(height: 10, color: Brand.s200),
            ),
            const SizedBox(height: 14),
            Text(title,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium),
            if (subtitle != null) ...[
              const SizedBox(height: 6),
              Text(subtitle!,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall),
            ],
            if (action != null) ...[const SizedBox(height: 16), action!],
          ],
        ),
      ),
    );
  }
}

class ErrorRetry extends StatelessWidget {
  final Object error;
  final VoidCallback onRetry;
  const ErrorRetry({super.key, required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return EmptyState(
      icon: Icons.cloud_off,
      title: 'Impossible de charger',
      subtitle: '$error',
      action: OutlinedButton.icon(
        onPressed: onRetry,
        icon: const Icon(Icons.refresh),
        label: const Text('Réessayer'),
      ),
    );
  }
}

/// Renders an [AsyncSnapshot]-like value from a Future with loading / error / data.
class FutureView<T> extends StatelessWidget {
  final Future<T> future;
  final Widget Function(T data) builder;
  final VoidCallback onRetry;
  const FutureView({
    super.key,
    required this.future,
    required this.builder,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<T>(
      future: future,
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return ErrorRetry(error: snap.error!, onRetry: onRetry);
        }
        return builder(snap.data as T);
      },
    );
  }
}

void showSnack(BuildContext context, String message, {bool error = false}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(message),
      backgroundColor: error ? const Color(0xFF991B1B) : null,
    ));
}
