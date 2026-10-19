import 'dart:async';

import 'package:flutter/material.dart';

import '../../theme/game_palette.dart';
import '../../widgets/guide_character/guide_character.dart';
import '../../widgets/orchard_girl.dart' show OrchardGirlController, OrchardGirlEvent, OrchardGirlMood;

/// Development-only gallery for the jointed guide rig.
///
/// Never linked from the player UI. Open it from a debug entry point (or a
/// widget test) to inspect all seven moods, a forced blink, the Reduce Motion
/// pose and the rig at the exact sizes the game uses.
class GuideRigLabScreen extends StatefulWidget {
  const GuideRigLabScreen({super.key});

  @override
  State<GuideRigLabScreen> createState() => _GuideRigLabScreenState();
}

class _GuideRigLabScreenState extends State<GuideRigLabScreen> {
  final OrchardGirlController _controller = OrchardGirlController();
  OrchardGirlMood _mood = OrchardGirlMood.idle;
  bool _reduceMotion = false;
  bool _compareRest = false;
  double _height = 220;
  Timer? _cycle;

  static const List<OrchardGirlMood> _moods = OrchardGirlMood.values;

  /// The height the gameplay screen actually uses on a 360x800 phone.
  static const double _gameHeight = 190;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _play(_mood));
  }

  @override
  void dispose() {
    _cycle?.cancel();
    _controller.dispose();
    super.dispose();
  }

  static OrchardGirlEvent _eventFor(OrchardGirlMood mood) => switch (mood) {
        OrchardGirlMood.idle => OrchardGirlEvent.validHint,
        OrchardGirlMood.happy => OrchardGirlEvent.goalsComplete,
        OrchardGirlMood.excited => OrchardGirlEvent.cascade,
        OrchardGirlMood.encourage => OrchardGirlEvent.nearGoal,
        OrchardGirlMood.sad => OrchardGirlEvent.levelLost,
        OrchardGirlMood.celebrate => OrchardGirlEvent.levelWon,
        OrchardGirlMood.harvest => OrchardGirlEvent.ordinaryMatch,
      };

  void _play(OrchardGirlMood mood) {
    setState(() => _mood = mood);
    _controller.react(_eventFor(mood));
  }

  void _cycleOnce() {
    _cycle?.cancel();
    var index = _moods.indexOf(_mood);
    _cycle = Timer.periodic(const Duration(milliseconds: 2600), (_) {
      index = (index + 1) % _moods.length;
      _play(_moods[index]);
    });
  }

  @override
  Widget build(BuildContext context) {
    // The rest comparison must show the delivered pose, so it renders a
    // controller-free character: idle + Reduce Motion = the untouched art.
    final character = _compareRest
        ? GuideCharacter(
            mood: OrchardGirlMood.idle,
            height: _height,
            reduceMotionOverride: true,
            paused: true,
          )
        : GuideCharacter(
            controller: _controller,
            height: _height,
            reduceMotionOverride: _reduceMotion,
          );
    return Scaffold(
      backgroundColor: GamePalette.forestDeep,
      appBar: AppBar(
        title: const Text('کارگاه ریگ کاراکتر'),
        backgroundColor: GamePalette.forest,
      ),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            Expanded(
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  decoration: BoxDecoration(
                    color: GamePalette.creamLight.withValues(alpha: 0.10),
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: character,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: <Widget>[
                  const Text('اندازهٔ واقعی', style: TextStyle(color: Colors.white70)),
                  Expanded(
                    child: Slider(
                      value: _height,
                      min: 90,
                      max: 320,
                      onChanged: (value) => setState(() => _height = value),
                    ),
                  ),
                  Text('${_height.round()}px',
                      style: const TextStyle(color: Colors.white70)),
                  TextButton(
                    onPressed: () => setState(() => _height = _gameHeight),
                    child: const Text('۱۹۰'),
                  ),
                ],
              ),
            ),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              alignment: WrapAlignment.center,
              children: <Widget>[
                for (final mood in _moods)
                  ChoiceChip(
                    label: Text(_label(mood)),
                    selected: _mood == mood,
                    onSelected: (_) => _play(mood),
                  ),
                ActionChip(
                  avatar: const Icon(Icons.play_arrow_rounded, size: 16),
                  label: const Text('چرخهٔ خودکار'),
                  onPressed: _cycleOnce,
                ),
              ],
            ),
            SwitchListTile(
              value: _reduceMotion,
              title: const Text('کاهش حرکت (Reduce Motion)'),
              onChanged: (value) => setState(() => _reduceMotion = value),
            ),
            SwitchListTile(
              value: _compareRest,
              title: const Text('مقایسه با حالت ساکن'),
              subtitle: const Text('حالت آرام و بدون حرکت، برای مقایسه با آرت اصلی'),
              onChanged: (value) => setState(() => _compareRest = value),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  static String _label(OrchardGirlMood mood) => switch (mood) {
        OrchardGirlMood.idle => 'آرام',
        OrchardGirlMood.happy => 'شاد',
        OrchardGirlMood.excited => 'هیجان',
        OrchardGirlMood.encourage => 'تشویق',
        OrchardGirlMood.sad => 'غمگین',
        OrchardGirlMood.celebrate => 'جشن',
        OrchardGirlMood.harvest => 'برداشت',
      };
}

/// Convenience for debug builds: a full-size stage that renders the guide at
/// 1:1 with the painted canvas, useful when comparing against the original art.
class GuideRigRestCompareScreen extends StatelessWidget {
  const GuideRigRestCompareScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: Color(0xFFF2EEE4),
      body: SafeArea(
        child: Center(
          child: GuideCharacter(height: 686, paused: true),
        ),
      ),
    );
  }
}
