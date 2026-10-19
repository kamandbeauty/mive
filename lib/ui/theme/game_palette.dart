import 'package:flutter/material.dart';

/// Colour and shape tokens for the orchard redesign.
///
/// Everything on the gameplay screen pulls from here so the header, HUD,
/// board and character zone cannot drift apart again.
abstract final class GamePalette {
  // Core palette from the art direction brief.
  static const Color forest = Color(0xFF267A3B);
  static const Color leaf = Color(0xFF59B94B);
  static const Color gold = Color(0xFFFFC83D);
  static const Color orange = Color(0xFFFF942E);
  static const Color strawberry = Color(0xFFF04448);
  static const Color sky = Color(0xFF35B8F5);
  static const Color cream = Color(0xFFFFF0D0);
  static const Color wood = Color(0xFF784020);
  static const Color chocolate = Color(0xFF59331F);

  // Derived tints used for panels and depth.
  static const Color creamLight = Color(0xFFFFF8E8);
  static const Color creamDeep = Color(0xFFF3DCB4);
  static const Color woodShadow = Color(0xFF4A2512);
  static const Color forestDeep = Color(0xFF14512A);

  /// Soft drop shadow shared by every floating panel.
  static List<BoxShadow> get panelShadow => <BoxShadow>[
        BoxShadow(
          color: woodShadow.withValues(alpha: 0.34),
          blurRadius: 18,
          offset: const Offset(0, 8),
        ),
        BoxShadow(
          color: Colors.white.withValues(alpha: 0.30),
          blurRadius: 3,
          offset: const Offset(0, -1),
        ),
      ];

  static List<BoxShadow> get chipShadow => <BoxShadow>[
        BoxShadow(
          color: woodShadow.withValues(alpha: 0.28),
          blurRadius: 8,
          offset: const Offset(0, 3),
        ),
      ];

  static const LinearGradient goldGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: <Color>[Color(0xFFFFE07A), gold, Color(0xFFF2A51F)],
  );

  /// Warm scrim that keeps the HUD readable over the orchard photo.
  static const LinearGradient screenScrim = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: <Color>[
      Color(0x66000000),
      Color(0x1A000000),
      Color(0x1A000000),
      Color(0x99142B12),
    ],
    stops: <double>[0, 0.22, 0.62, 1],
  );

  static const TextStyle numberStyle = TextStyle(
    color: chocolate,
    fontSize: 22,
    fontWeight: FontWeight.w900,
    height: 1.0,
  );

  static const TextStyle labelStyle = TextStyle(
    color: Color(0xFF7A5A3A),
    fontSize: 10,
    fontWeight: FontWeight.w800,
    height: 1.1,
  );

  static const TextStyle titleStyle = TextStyle(
    color: Color(0xFFFFF6E2),
    fontSize: 24,
    fontWeight: FontWeight.w900,
    height: 1.05,
    shadows: <Shadow>[
      Shadow(color: Color(0xAA3A1C08), blurRadius: 6, offset: Offset(0, 2)),
    ],
  );
}
