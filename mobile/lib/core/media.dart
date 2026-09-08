import 'package:flutter/material.dart';
import 'brand.dart';
import 'config.dart';

/// Rewrites a backend media URL so its host matches the configured API host.
/// See [AppConfig.resolveHost].
String? resolveMediaUrl(String? url) => AppConfig.resolveHost(url);

/// Network image with a branded placeholder while loading and on failure.
class RemoteImage extends StatelessWidget {
  final String? url;
  final double? width;
  final double? height;
  final BoxFit fit;
  final IconData fallbackIcon;

  const RemoteImage({
    super.key,
    required this.url,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.fallbackIcon = Icons.event,
  });

  @override
  Widget build(BuildContext context) {
    final resolved = resolveMediaUrl(url);
    final placeholder = Container(
      width: width,
      height: height,
      color: Brand.s100,
      alignment: Alignment.center,
      child: Icon(fallbackIcon, color: Brand.s300, size: 32),
    );
    if (resolved == null) return placeholder;
    return Image.network(
      resolved,
      width: width,
      height: height,
      fit: fit,
      gaplessPlayback: true,
      loadingBuilder: (context, child, progress) =>
          progress == null ? child : placeholder,
      errorBuilder: (context, error, stack) => placeholder,
    );
  }
}
