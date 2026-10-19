import 'package:flutter/material.dart';

import 'guide_rig_lab_screen.dart';

/// Standalone entry point for the jointed character workshop:
///
///     flutter run -t lib/ui/features/character_lab/main.dart
///
/// It exists only for development and is not part of the player navigation.
void main() => runApp(const _CharacterLabApp());

class _CharacterLabApp extends StatelessWidget {
  const _CharacterLabApp();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'کارگاه کاراکتر',
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFFE07A5F),
      ),
      home: const GuideRigLabScreen(),
    );
  }
}
