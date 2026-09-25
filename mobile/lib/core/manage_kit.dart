import 'package:flutter/material.dart';
import 'brand.dart';

/// Building blocks shared by the management screens (organiser, control, admin): one card / tile / notice
/// language so "Mes événements", the editors and the scanner read as one product.

/// Semantic colour pairs (soft background + strong foreground).
enum KitTone {
  green(Brand.b50, Brand.b700),
  blue(Color(0xFFEFF6FF), Color(0xFF1D4ED8)),
  amber(Color(0xFFFFFBEB), Color(0xFFB45309)),
  red(Color(0xFFFEF2F2), Color(0xFFB91C1C)),
  violet(Color(0xFFF5F3FF), Color(0xFF6D28D9)),
  slate(Brand.s100, Brand.s700);

  final Color bg;
  final Color fg;
  const KitTone(this.bg, this.fg);
}

/// Centres the page content and caps its width, so forms and lists do not stretch edge to edge on
/// tablets and in the browser.
class MaxWidth extends StatelessWidget {
  final double width;
  final Widget child;
  const MaxWidth({super.key, this.width = 720, required this.child});

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    child: ConstrainedBox(
      constraints: BoxConstraints(maxWidth: width),
      child: child,
    ),
  );
}

/// Tinted rounded square holding an icon.
class IconBubble extends StatelessWidget {
  final IconData icon;
  final KitTone tone;
  final double size;
  const IconBubble(
    this.icon, {
    super.key,
    this.tone = KitTone.green,
    this.size = 40,
  });

  @override
  Widget build(BuildContext context) => Container(
    width: size,
    height: size,
    decoration: BoxDecoration(
      color: tone.bg,
      borderRadius: BorderRadius.circular(size * .3),
    ),
    alignment: Alignment.center,
    child: Icon(icon, size: size * .52, color: tone.fg),
  );
}

/// Bordered card with a small heading; its [children] are separated by hairlines.
class SectionCard extends StatelessWidget {
  final String? title;
  final Widget? trailing;
  final List<Widget> children;
  final bool divided;
  final EdgeInsetsGeometry? padding;

  const SectionCard({
    super.key,
    this.title,
    this.trailing,
    required this.children,
    this.divided = true,
    this.padding,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 8, 6),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      title!.toUpperCase(),
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        letterSpacing: .8,
                        color: Brand.s500,
                      ),
                    ),
                  ),
                  if (trailing != null) trailing!,
                ],
              ),
            ),
          Padding(
            padding: padding ?? EdgeInsets.zero,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (int i = 0; i < children.length; i++) ...[
                  children[i],
                  if (divided && i < children.length - 1)
                    const Divider(height: 1, indent: 16, endIndent: 16),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Tappable row: icon bubble, title, optional subtitle and badge, chevron.
class ActionTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final String? badge;
  final KitTone tone;
  final VoidCallback? onTap;

  const ActionTile({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.badge,
    this.tone = KitTone.green,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Opacity(
          opacity: enabled ? 1 : .5,
          child: Row(
            children: [
              IconBubble(icon, tone: tone),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: Brand.s800,
                      ),
                    ),
                    if (subtitle != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          subtitle!,
                          style: const TextStyle(
                            fontSize: 13,
                            color: Brand.s500,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              if (badge != null) ...[
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: tone.bg,
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: Text(
                    badge!,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: tone.fg,
                    ),
                  ),
                ),
              ],
              if (enabled) const Icon(Icons.chevron_right, color: Brand.s400),
            ],
          ),
        ),
      ),
    );
  }
}

/// Coloured notice: information, warning, error or success.
class InfoBanner extends StatelessWidget {
  final String text;
  final KitTone tone;
  final IconData? icon;
  final Widget? action;

  const InfoBanner(
    this.text, {
    super.key,
    this.tone = KitTone.slate,
    this.icon,
    this.action,
  });

  @override
  Widget build(BuildContext context) {
    final glyph =
        icon ??
        switch (tone) {
          KitTone.red => Icons.error_outline,
          KitTone.amber => Icons.info_outline,
          KitTone.green => Icons.check_circle_outline,
          _ => Icons.info_outline,
        };
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: tone.bg,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: tone.fg.withValues(alpha: .18)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(glyph, size: 20, color: tone.fg),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  text,
                  style: TextStyle(
                    fontSize: 13.5,
                    height: 1.35,
                    color: tone.fg,
                  ),
                ),
                if (action != null) ...[const SizedBox(height: 8), action!],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// A figure with its label — the tiles of a summary strip.
class KpiTile extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final KitTone tone;

  /// 0..1: draws a thin progress bar (e.g. tickets sold out of the total).
  final double? progress;

  const KpiTile({
    super.key,
    required this.icon,
    required this.value,
    required this.label,
    this.tone = KitTone.green,
    this.progress,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Brand.s200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconBubble(icon, tone: tone, size: 30),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 12, color: Brand.s500),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              value,
              style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w800,
                color: Brand.s900,
              ),
            ),
          ),
          if (progress != null) ...[
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(99),
              child: LinearProgressIndicator(
                value: progress!.clamp(0, 1).toDouble(),
                minHeight: 5,
                backgroundColor: tone.bg,
                color: tone.fg,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Two tiles per row, whatever the width (a strip of [KpiTile]s).
class KpiGrid extends StatelessWidget {
  final List<Widget> tiles;
  const KpiGrid({super.key, required this.tiles});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, c) {
      const gap = 10.0;
      final cols = c.maxWidth >= 620 ? 4 : 2;
      final w = (c.maxWidth - gap * (cols - 1)) / cols;
      return Wrap(
        spacing: gap,
        runSpacing: gap,
        children: [for (final t in tiles) SizedBox(width: w, child: t)],
      );
    },
  );
}

/// Horizontally scrolling single-choice pills ("Tous", "Brouillons"…), with an optional count.
class FilterPills<T> extends StatelessWidget {
  final List<({T value, String label, int? count})> items;
  final T selected;
  final ValueChanged<T> onSelected;
  const FilterPills({
    super.key,
    required this.items,
    required this.selected,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 40,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (_, i) {
          final it = items[i];
          final on = it.value == selected;
          return GestureDetector(
            onTap: () => onSelected(it.value),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              padding: const EdgeInsets.symmetric(horizontal: 14),
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: on ? Brand.b600 : Colors.white,
                borderRadius: BorderRadius.circular(99),
                border: Border.all(color: on ? Brand.b600 : Brand.s200),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    it.label,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: on ? Colors.white : Brand.s700,
                    ),
                  ),
                  if (it.count != null) ...[
                    const SizedBox(width: 6),
                    Text(
                      '${it.count}',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: on ? Colors.white70 : Brand.s400,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Bottom bar for forms: the primary action stays reachable however long the form is.
class StickyActionBar extends StatelessWidget {
  final Widget child;
  const StickyActionBar({super.key, required this.child});

  @override
  Widget build(BuildContext context) => Container(
    decoration: const BoxDecoration(
      color: Colors.white,
      border: Border(top: BorderSide(color: Brand.s200)),
    ),
    child: SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
        child: MaxWidth(child: child),
      ),
    ),
  );
}

/// Progress of a quota: "120 / 300" with a bar, coloured by how full it is.
class QuotaBar extends StatelessWidget {
  final int used;
  final int total;
  final String label;
  const QuotaBar({
    super.key,
    required this.used,
    required this.total,
    this.label = 'vendus',
  });

  @override
  Widget build(BuildContext context) {
    final ratio = total <= 0 ? 0.0 : (used / total).clamp(0, 1).toDouble();
    final tone =
        ratio >= 1
            ? KitTone.red
            : ratio >= .8
            ? KitTone.amber
            : KitTone.green;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              '$used / $total $label',
              style: const TextStyle(fontSize: 12, color: Brand.s500),
            ),
            const Spacer(),
            Text(
              '${(ratio * 100).round()} %',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: tone.fg,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        ClipRRect(
          borderRadius: BorderRadius.circular(99),
          child: LinearProgressIndicator(
            value: ratio,
            minHeight: 5,
            backgroundColor: tone.bg,
            color: tone.fg,
          ),
        ),
      ],
    );
  }
}
