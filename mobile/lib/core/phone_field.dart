import 'package:flutter/material.dart';
import 'countries.dart';

/// Phone number field split in two: a country-code dropdown and a
/// local-number field (typed without the dial code). [onChanged] receives
/// the combined value (e.g. `"+226 70000000"`), matching the backend's
/// phone pattern directly. Pass [initialValue] to prefill/edit an existing
/// number — the matching dial code is detected automatically.
class PhoneField extends StatefulWidget {
  final String? initialValue;
  final ValueChanged<String> onChanged;
  final String label;
  final bool required;

  const PhoneField({
    super.key,
    this.initialValue,
    required this.onChanged,
    this.label = 'Téléphone',
    this.required = false,
  });

  @override
  State<PhoneField> createState() => _PhoneFieldState();
}

class _PhoneFieldState extends State<PhoneField> {
  late String _dialCode;
  late final TextEditingController _number;

  @override
  void initState() {
    super.initState();
    final (dialCode, number) = _split(widget.initialValue);
    _dialCode = dialCode;
    _number = TextEditingController(text: number);
  }

  @override
  void didUpdateWidget(PhoneField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.initialValue != oldWidget.initialValue &&
        widget.initialValue != _combined()) {
      final (dialCode, number) = _split(widget.initialValue);
      setState(() {
        _dialCode = dialCode;
        _number.text = number;
      });
    }
  }

  /// Longest dial code first so "+1" doesn't swallow "+1 868" (Trinidad).
  (String, String) _split(String? value) {
    final trimmed = value?.trim() ?? '';
    if (trimmed.isEmpty) return ('+226', '');
    final sorted = [...kCountryCallingCodes]
      ..sort((a, b) => b.phoneCode.length.compareTo(a.phoneCode.length));
    for (final c in sorted) {
      if (trimmed.startsWith(c.phoneCode)) {
        return (c.phoneCode, trimmed.substring(c.phoneCode.length).trim());
      }
    }
    return ('+226', trimmed);
  }

  String _combined() {
    final digits = _number.text.replaceAll(RegExp(r'[^0-9]'), '');
    return digits.isEmpty ? '' : '$_dialCode $digits';
  }

  void _emit() => widget.onChanged(_combined());

  @override
  Widget build(BuildContext context) {
    final dial = DropdownButtonFormField<String>(
      value: _dialCode,
      isExpanded: true,
      menuMaxHeight: 360,
      decoration: const InputDecoration(labelText: 'Indicatif'),
      items: kCountryCallingCodes
          .map((c) => DropdownMenuItem(
                value: c.phoneCode,
                child: Text('${c.code} ${c.phoneCode}',
                    overflow: TextOverflow.ellipsis),
              ))
          .toList(),
      onChanged: (v) {
        setState(() => _dialCode = v ?? '+226');
        _emit();
      },
    );
    final number = TextFormField(
      controller: _number,
      keyboardType: TextInputType.phone,
      decoration: InputDecoration(
        labelText: widget.required ? '${widget.label} *' : widget.label,
      ),
      validator: widget.required
          ? (v) => (v == null || v.trim().isEmpty) ? 'Requis' : null
          : null,
      onChanged: (_) => _emit(),
    );

    return LayoutBuilder(builder: (context, c) {
      // A very narrow field, or a large system font, cannot fit both parts
      // side by side: stack them instead of squeezing the number field.
      final scale = MediaQuery.textScalerOf(context).scale(1);
      if (c.maxWidth < 280 || (c.maxWidth < 340 && scale > 1.3)) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [dial, const SizedBox(height: 12), number],
        );
      }
      final dialWidth = (c.maxWidth * .36).clamp(108.0, 136.0);
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: dialWidth, child: dial),
          const SizedBox(width: 8),
          Expanded(child: number),
        ],
      );
    });
  }

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }
}
