import 'package:flutter/material.dart';
import 'brand.dart';
import 'config.dart';

/// Rewrites a backend media URL so its host matches the configured API host.
///
/// The backend serves files at `http://localhost:8080/files/...`; that host is
/// unreachable from an Android emulator (`10.0.2.2`) or a physical device (LAN
/// IP). External URLs are returned untouched.
String? resolveMediaUrl(String? url) {
  if (url == null || url.trim().isEmpty) return null;
  final api = Uri.tryParse(AppConfig.apiBaseUrl);
  final u = Uri.tryParse(url.trim());
  if (api == null || u == null || !u.hasScheme) return url;
  const localHosts = {'localhost', '127.0.0.1', '10.0.2.2', '0.0.0.0'};
  if (localHosts.contains(u.host) || u.host == api.host) {
    return u
        .replace(
          scheme: api.scheme,
          host: api.host,
          port: api.hasPort ? api.port : (u.hasPort ? u.port : null),
        )
        .toString();
  }
  return url;
}

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
