import 'package:flutter/material.dart';

import '../../widgets/orchard_girl.dart';

/// Development-only gallery for the layered character.
///
/// It is never linked from the player UI; open it from a debug entry point or
/// a widget test. It lets us eyeball every mood, force a blink, toggle Reduce
/// Motion and check the rig at the sizes the game actually uses.
class CharacterLabScreen extends StatefulWidget {
  const CharacterLabScreen({super.key});

  @override
  State<CharacterLabScreen> createState() => _CharacterLabScreenState();
}

class _CharacterLabScreenState extends State<CharacterLabScreen> {
  final OrchardGirlController _controller = OrchardGirlController();
  OrchardGirlMood _mood = OrchardGirlMood.idle;
  bool _reduceMotion = false;
  double _scale = 1;

  static const List<OrchardGirlMood> _moods = OrchardGirlMood.values;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _select(OrchardGirlMood mood) {
    setState(() => _mood = mood);
    _controller.react(_eventFor(mood));
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('کارگاه کاراکتر')),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: Center(
                child: OrchardGirl(
                  controller: _controller,
                  width: 220 * _scale,
                  height: 330 * _scale,
                  showCaption: true,
                  reduceMotionOverride: _reduceMotion,
                ),
              ),
            ),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              alignment: WrapAlignment.center,
              children: [
                for (final mood in _moods)
                  ChoiceChip(
                    label: Text(_label(mood)),
                    selected: _mood == mood,
                    onSelected: (_) => _select(mood),
                  ),
              ],
            ),
            SwitchListTile(
              value: _reduceMotion,
              title: const Text('کاهش حرکت (Reduce Motion)'),
              onChanged: (value) => setState(() => _reduceMotion = value),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Row(
                children: [
                  const Text('اندازه'),
                  Expanded(
                    child: Slider(
                      value: _scale,
                      min: 0.3,
                      max: 1.4,
                      onChanged: (value) => setState(() => _scale = value),
                    ),
                  ),
                  Text(_scale.toStringAsFixed(2)),
                ],
              ),
            ),
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
