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
/// tablets and in the browser. It only takes the width: its height is its child's (a bottom bar must
/// not swallow the whole screen), while a scrollable child still fills the room it is given.
class MaxWidth extends StatelessWidget {
  final double width;
  final Widget child;
  const MaxWidth({super.key, this.width = 720, required this.child});

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    heightFactor: 1,
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

  /// Small line under the figure (e.g. "+4 en attente").
  final String? caption;

  const KpiTile({
    super.key,
    required this.icon,
    required this.value,
    required this.label,
    this.tone = KitTone.green,
    this.progress,
    this.caption,
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
          if (caption != null)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                caption!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: tone.fg,
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

/// Two tiles per row (four on wide screens); the tiles of a row share the same height.
class KpiGrid extends StatelessWidget {
  final List<Widget> tiles;
  const KpiGrid({super.key, required this.tiles});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, c) {
      const gap = 10.0;
      final cols = c.maxWidth >= 620 ? 4 : 2;
      final rows = <Widget>[];
      for (var i = 0; i < tiles.length; i += cols) {
        final chunk = tiles.sublist(i, (i + cols).clamp(0, tiles.length));
        rows.add(
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var k = 0; k < cols; k++) ...[
                  if (k > 0) const SizedBox(width: gap),
                  Expanded(
                    child: k < chunk.length ? chunk[k] : const SizedBox(),
                  ),
                ],
              ],
            ),
          ),
        );
      }
      return Column(
        children: [
          for (var r = 0; r < rows.length; r++) ...[
            if (r > 0) const SizedBox(height: gap),
            rows[r],
          ],
        ],
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

/// A titled group of form fields on a card: icon bubble, title, optional hint, then the fields.
class FormSection extends StatelessWidget {
  final String title;
  final IconData icon;
  final KitTone tone;
  final String? hint;
  final List<Widget> children;

  const FormSection({
    super.key,
    required this.title,
    required this.icon,
    required this.children,
    this.tone = KitTone.green,
    this.hint,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                IconBubble(icon, tone: tone, size: 34),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: Theme.of(context).textTheme.titleSmall),
                      if (hint != null)
                        Text(
                          hint!,
                          style: const TextStyle(fontSize: 12.5, color: Brand.s500),
                        ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            for (int i = 0; i < children.length; i++) ...[
              if (i > 0) const SizedBox(height: 12),
              children[i],
            ],
          ],
        ),
      ),
    );
  }
}

/// Date-and-time field that looks like the text fields: tap to pick, optional clear button.
class DateField extends StatelessWidget {
  final String label;
  final DateTime? value;
  final String Function(DateTime) format;
  final VoidCallback onTap;
  final VoidCallback? onClear;
  final String? errorText;

  const DateField({
    super.key,
    required this.label,
    required this.value,
    required this.format,
    required this.onTap,
    this.onClear,
    this.errorText,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: InputDecorator(
        isEmpty: value == null,
        decoration: InputDecoration(
          labelText: label,
          hintText: 'Choisir la date et l\'heure',
          errorText: errorText,
          suffixIcon: onClear != null && value != null
              ? IconButton(
                  tooltip: 'Effacer',
                  icon: const Icon(Icons.close, size: 20),
                  onPressed: onClear,
                )
              : const Icon(Icons.calendar_month_outlined, color: Brand.s500),
        ),
        child: value == null
            ? const SizedBox.shrink()
            : Text(format(value!), style: const TextStyle(fontSize: 16, color: Brand.s800)),
      ),
    );
  }
}

/// Small coloured pill (price, quota, "inactif"…).
class MiniChip extends StatelessWidget {
  final String text;
  final KitTone tone;
  final IconData? icon;
  const MiniChip(this.text, {super.key, this.tone = KitTone.slate, this.icon});

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: tone.bg,
      borderRadius: BorderRadius.circular(99),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 12, color: tone.fg),
          const SizedBox(width: 4),
        ],
        Text(
          text,
          style: TextStyle(
            fontSize: 11.5,
            fontWeight: FontWeight.w700,
            color: tone.fg,
          ),
        ),
      ],
    ),
  );
}

/// One entry of an [ItemCard]'s "⋮" menu.
class ItemAction {
  final String label;
  final IconData icon;
  final VoidCallback onSelected;
  final bool destructive;
  const ItemAction(
    this.label,
    this.icon,
    this.onSelected, {
    this.destructive = false,
  });
}

/// A list item of an editor: leading icon (or photo), title, chips, optional footer, and a "⋮" menu.
class ItemCard extends StatelessWidget {
  final IconData icon;
  final KitTone tone;
  final Widget? leading;
  final String title;
  final String? subtitle;
  final List<Widget> chips;
  final Widget? footer;
  final VoidCallback? onTap;
  final List<ItemAction> actions;

  const ItemCard({
    super.key,
    required this.title,
    this.icon = Icons.circle_outlined,
    this.tone = KitTone.green,
    this.leading,
    this.subtitle,
    this.chips = const [],
    this.footer,
    this.onTap,
    this.actions = const [],
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      margin: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  leading ?? IconBubble(icon, tone: tone, size: 44),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            title,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              color: Brand.s800,
                            ),
                          ),
                          if (subtitle != null && subtitle!.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                subtitle!,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 13,
                                  color: Brand.s500,
                                ),
                              ),
                            ),
                          if (chips.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 8),
                              child: Wrap(
                                spacing: 6,
                                runSpacing: 6,
                                children: chips,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                  if (actions.isNotEmpty)
                    PopupMenuButton<int>(
                      tooltip: 'Actions',
                      icon: const Icon(Icons.more_vert, color: Brand.s500),
                      onSelected: (i) => actions[i].onSelected(),
                      itemBuilder: (_) => [
                        for (var i = 0; i < actions.length; i++)
                          PopupMenuItem<int>(
                            value: i,
                            child: Row(
                              children: [
                                Icon(
                                  actions[i].icon,
                                  size: 20,
                                  color: actions[i].destructive
                                      ? Brand.red
                                      : Brand.s700,
                                ),
                                const SizedBox(width: 12),
                                Text(
                                  actions[i].label,
                                  style: TextStyle(
                                    color: actions[i].destructive
                                        ? Brand.red
                                        : Brand.s800,
                                  ),
                                ),
                              ],
                            ),
                          ),
                      ],
                    )
                  else
                    const SizedBox(width: 8),
                ],
              ),
              if (footer != null)
                Padding(
                  padding: const EdgeInsets.fromLTRB(0, 10, 8, 0),
                  child: footer,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Asks before a destructive or irreversible action; true when the person confirmed.
Future<bool> confirmAction(
  BuildContext context, {
  required String title,
  String? message,
  String confirmLabel = 'Confirmer',
  bool destructive = false,
}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      content: message == null ? null : Text(message),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('Annuler'),
        ),
        FilledButton(
          style: destructive
              ? FilledButton.styleFrom(backgroundColor: Brand.red)
              : null,
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return ok == true;
}

/// Bottom sheet for creating / editing one item, capped in width for tablets.
Future<T?> showEditorSheet<T>(BuildContext context, WidgetBuilder builder) =>
    showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      backgroundColor: Colors.white,
      constraints: const BoxConstraints(maxWidth: 640),
      builder: builder,
    );

/// Layout of an editor sheet: header (icon, title, close), scrollable fields, primary action pinned below.
class SheetScaffold extends StatelessWidget {
  final String title;
  final String? subtitle;
  final IconData icon;
  final KitTone tone;
  final List<Widget> children;
  final Widget action;

  const SheetScaffold({
    super.key,
    required this.title,
    required this.icon,
    required this.children,
    required this.action,
    this.subtitle,
    this.tone = KitTone.green,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 8, 10),
              child: Row(
                children: [
                  IconBubble(icon, tone: tone, size: 38),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        if (subtitle != null)
                          Text(
                            subtitle!,
                            style: const TextStyle(
                              fontSize: 12.5,
                              color: Brand.s500,
                            ),
                          ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Fermer',
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (var i = 0; i < children.length; i++) ...[
                      if (i > 0) const SizedBox(height: 12),
                      children[i],
                    ],
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
              child: action,
            ),
          ],
        ),
      ),
    );
  }
}
