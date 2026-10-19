import 'package:flutter/material.dart';

import '../../theme/game_palette.dart';

/// Image-backed panel. Every surface in the game is drawn from the delivered
/// UI kit instead of hand-made gradients, so the wood grain and highlights stay
/// consistent.
class OrchardPanel extends StatelessWidget {
  const OrchardPanel({
    required this.asset,
    required this.child,
    this.padding = EdgeInsets.zero,
    this.fit = BoxFit.fill,
    super.key,
  });

  final String asset;
  final Widget child;
  final EdgeInsetsGeometry padding;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        Positioned.fill(
          child: Image.asset(asset, fit: fit, filterQuality: FilterQuality.medium),
        ),
        Padding(padding: padding, child: child),
      ],
    );
  }
}

/// Round, tactile button built on the kit's gold / wood / power-up plates.
class OrchardIconButton extends StatelessWidget {
  const OrchardIconButton({
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.size = 46,
    this.plate = 'assets/ui/button-wood.png',
    this.iconColor = const Color(0xFFFFF3D8),
    this.semanticKey,
    super.key,
  });

  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final double size;
  final String plate;
  final Color iconColor;
  final Key? semanticKey;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    final button = Opacity(
      opacity: enabled ? 1 : 0.55,
      child: SizedBox(
        width: size,
        height: size,
        child: Stack(
          alignment: Alignment.center,
          children: <Widget>[
            Positioned.fill(
              child: Image.asset(plate, fit: BoxFit.contain),
            ),
            Icon(icon, size: size * 0.46, color: iconColor),
          ],
        ),
      ),
    );
    final tappable = Semantics(
      button: true,
      enabled: enabled,
      label: tooltip,
      child: GestureDetector(
        key: semanticKey,
        behavior: HitTestBehavior.opaque,
        onTap: onPressed,
        child: button,
      ),
    );
    if (tooltip == null) return tappable;
    return Tooltip(message: tooltip!, child: tappable);
  }
}

/// Small parchment counter used on boosters and the level badge.
class OrchardBadge extends StatelessWidget {
  const OrchardBadge({
    required this.label,
    this.color = GamePalette.gold,
    this.textColor = GamePalette.chocolate,
    this.minWidth = 18,
    super.key,
  });

  final String label;
  final Color color;
  final Color textColor;
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(minWidth: minWidth),
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(9),
        border: Border.all(color: const Color(0xFFFFF6E0), width: 1),
        boxShadow: GamePalette.chipShadow,
      ),
      child: Text(
        label,
        textAlign: TextAlign.center,
        style: TextStyle(
          color: textColor,
          fontSize: 10,
          fontWeight: FontWeight.w900,
          height: 1.15,
        ),
      ),
    );
  }
}

/// Wooden track with a glossy green fill, driven by a 0..1 value.
class OrchardProgressBar extends StatelessWidget {
  const OrchardProgressBar({required this.value, this.height = 14, super.key});

  final double value;
  final double height;

  @override
  Widget build(BuildContext context) {
    final clamped = value.isNaN ? 0.0 : value.clamp(0.0, 1.0);
    return SizedBox(
      height: height,
      child: Stack(
        fit: StackFit.expand,
        children: <Widget>[
          Image.asset('assets/ui/progress-track.png', fit: BoxFit.fill),
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: FractionallySizedBox(
              widthFactor: clamped,
              heightFactor: 1,
              child: Image.asset('assets/ui/progress-fill.png', fit: BoxFit.fill),
            ),
          ),
        ],
      ),
    );
  }
}

/// Cream speech bubble with the kit's tail.
///
/// All padding is proportional, so the bubble stays valid when the layout gives
/// it very little room on a short screen; below [_minHeight] it simply hides.
class OrchardBubble extends StatelessWidget {
  const OrchardBubble({required this.message, this.maxWidth = 220, super.key});

  final String message;
  final double maxWidth;

  static const double _aspect = 480 / 244;
  static const double _minHeight = 46;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(maxWidth: maxWidth),
      child: AspectRatio(
        aspectRatio: _aspect,
        child: LayoutBuilder(
          builder: (context, constraints) {
            if (!constraints.hasBoundedHeight ||
                constraints.maxHeight < _minHeight) {
              return const SizedBox.shrink();
            }
            final width = constraints.maxWidth;
            final height = constraints.maxHeight;
            return Stack(
              fit: StackFit.expand,
              children: <Widget>[
                Image.asset('assets/ui/dialogue-bubble.png', fit: BoxFit.fill),
                Padding(
                  padding: EdgeInsets.fromLTRB(
                    width * 0.05,
                    height * 0.08,
                    width * 0.05,
                    height * 0.26,
                  ),
                  child: Center(
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        message,
                        textAlign: TextAlign.center,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: GamePalette.chocolate,
                          fontSize: 12,
                          height: 1.35,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
