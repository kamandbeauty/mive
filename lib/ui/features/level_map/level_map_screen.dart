import 'package:flutter/material.dart';

import '../../../game/models/economy_progress.dart';
import '../../../game/models/game_progress.dart';
import '../../../game/models/level_definition.dart';
import '../../../game/services/economy_repository.dart';
import '../../../game/services/level_catalog.dart';
import '../../../game/services/progress_repository.dart';
import '../../../game/services/rewarded_ad_service.dart';
import '../gem_match/gem_match_game.dart';
import '../../widgets/guide_character/guide_character.dart';
import '../../widgets/orchard_girl.dart';
import 'map_chest_marker.dart';
import 'map_chests.dart';

const _leaf = Color(0xFF497C47);
const _brown = Color(0xFF68492F);
const _cream = Color(0xFFFFF2DE);

class LevelMapScreen extends StatefulWidget {
  const LevelMapScreen({
    this.catalog,
    this.initialProgress,
    this.initialEconomy,
    this.randomSeed,
    this.rewardedAds,
    this.economyRepository,
    this.onRequestConsent,
    super.key,
  })  : assert((catalog == null) == (initialProgress == null)),
        assert((rewardedAds == null) == (economyRepository == null));

  /// Optional preloaded data keeps widget tests deterministic and avoids
  /// coupling the map presentation to storage timing.
  final LevelCatalog? catalog;
  final GameProgress? initialProgress;
  final EconomyProgress? initialEconomy;
  /// Optional deterministic board seed for integration-test level fixtures.
  final int? randomSeed;
  final RewardedAdService? rewardedAds;
  final EconomyRepository? economyRepository;
  final Future<void> Function()? onRequestConsent;

  @override
  State<LevelMapScreen> createState() => _LevelMapScreenState();
}

class _LevelMapScreenState extends State<LevelMapScreen> {
  final ProgressRepository _progressRepository = ProgressRepository();
  final EconomyRepository _localEconomyRepository = EconomyRepository();
  EconomyRepository get _economyRepository =>
      widget.economyRepository ?? _localEconomyRepository;
  late Future<_MapData> _data;

  @override
  void initState() {
    super.initState();
    final catalog = widget.catalog;
    final progress = widget.initialProgress;
    _data = catalog != null && progress != null
        ? Future<_MapData>.value(
            _MapData(catalog, progress, widget.initialEconomy ?? const EconomyProgress()),
          )
        : _load();
  }

  Future<_MapData> _load() async {
    final results = await Future.wait<Object>([
      LevelCatalog.load(),
      _progressRepository.load(),
      _economyRepository.load(),
    ]);
    return _MapData(
      results[0] as LevelCatalog,
      results[1] as GameProgress,
      results[2] as EconomyProgress,
    );
  }

  Future<void> _openSettings(GameProgress initial) async {
    var soundEnabled = initial.soundEnabled;
    var hapticsEnabled = initial.hapticsEnabled;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: _cream,
          title: const Text('تنظیمات بازی', style: TextStyle(color: _brown, fontWeight: FontWeight.w900)),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('صدای کلیک سیستم', style: TextStyle(color: _brown)),
                value: soundEnabled,
                onChanged: (value) async {
                  setDialogState(() => soundEnabled = value);
                  await _progressRepository.setSettings(soundEnabled: value);
                  if (mounted) {
                    setState(() {
                      _data = _load();
                    });
                  }
                },
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('لرزش لمسی', style: TextStyle(color: _brown)),
                value: hapticsEnabled,
                onChanged: (value) async {
                  setDialogState(() => hapticsEnabled = value);
                  await _progressRepository.setSettings(hapticsEnabled: value);
                  if (mounted) {
                    setState(() {
                      _data = _load();
                    });
                  }
                },
              ),
            ],
          ),
          actions: [TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('بستن'))],
        ),
      ),
    );
  }

  Future<void> _openChest(int levelId, MapChestReward reward) async {
    final ads = widget.rewardedAds;
    if (ads == null || !ads.config.supports(RewardedUseCase.mapChest)) return;
    if (!ads.consentGranted) {
      await widget.onRequestConsent?.call();
      if (!ads.consentGranted) return;
    }
    final chestId = 'grove-milestone-$levelId';
    final result = await ads.show(
      useCase: RewardedUseCase.mapChest,
      claimId: 'map-chest:$chestId',
      onEarned: (claimId) async {
        final granted = await _economyRepository.claimMapChest(
          chestId: chestId,
          coins: reward.coins,
          boosters: {reward.booster: reward.boosterCount},
        );
        if (!granted) throw StateError('Map chest $chestId was already opened.');
      },
    );
    if (!mounted) return;
    setState(() {
      _data = _load();
    });
    final message = switch (result.outcome) {
      RewardedAdOutcome.earned => 'صندوق مرحلهٔ ${_fa(levelId)} به کیف باغ اضافه شد.',
      RewardedAdOutcome.dismissed => 'تبلیغ بسته شد؛ صندوق باز نشد.',
      RewardedAdOutcome.unavailable => 'تبلیغ آماده نیست؛ بعداً دوباره تلاش کن.',
      RewardedAdOutcome.alreadyClaimed => 'این صندوق قبلاً دریافت شده است.',
      RewardedAdOutcome.busy => 'یک تبلیغ دیگر هنوز در حال اجراست.',
      RewardedAdOutcome.failed => 'صندوق باز نشد؛ پاداشی ثبت نشد.',
    };
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _openLevel(LevelDefinition level) async {
    final progress = await _progressRepository.setLastPlayedLevel(level.id);
    final economy = await _economyRepository.load();
    if (!mounted) return;
    await Navigator.of(context).push<void>(MaterialPageRoute(
      builder: (_) => GemMatchGame(
        level: level,
        randomSeed: widget.randomSeed,
        soundEnabled: progress.soundEnabled,
        hapticsEnabled: progress.hapticsEnabled,
        boosterInventory: economy.boosters,
        onUseBooster: _economyRepository.consumeBooster,
        onCompleted: (levelId, score, stars) async {
          await _progressRepository.saveCompletion(
            levelId: levelId,
            stars: stars,
            score: score,
          );
        },
        onRequestExtraMoves: widget.rewardedAds == null
            ? null
            : (levelId) async {
                final ads = widget.rewardedAds!;
                final economyRepository = widget.economyRepository!;
                if (!ads.consentGranted) await widget.onRequestConsent?.call();
                if (!ads.consentGranted) return false;
                final economy = await economyRepository.load();
                if (economy.extraMoveLevels.contains(levelId)) return false;
                final claimId = 'extra-moves:level:$levelId';
                final result = await ads.show(
                  useCase: RewardedUseCase.extraMoves,
                  claimId: claimId,
                  onEarned: (id) async {
                    final granted = await economyRepository.claimExtraMoves(
                      levelId: levelId,
                      claimId: id,
                    );
                    if (!granted) {
                      throw StateError('Extra moves already granted for level $levelId.');
                    }
                  },
                );
                return result.earned;
              },
      ),
    ));
    if (!mounted) return;
    setState(() {
      _data = _load();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFD9EDB9), Color(0xFFA7CE8A), Color(0xFF759F60)],
          ),
        ),
        child: SafeArea(
          child: FutureBuilder<_MapData>(
            future: _data,
            builder: (context, snapshot) {
              if (snapshot.hasError) {
                return Center(child: Text('بارگذاری مراحل ناموفق بود\n${snapshot.error}', textAlign: TextAlign.center));
              }
              if (!snapshot.hasData) {
                return const Center(child: CircularProgressIndicator(color: _leaf));
              }
              return _buildMap(snapshot.data!);
            },
          ),
        ),
      ),
    );
  }

  Widget _buildMap(_MapData data) {
    final progress = data.progress;
    final nextLevel = data.catalog.byId(progress.lastPlayedLevel) ?? data.catalog.levels.first;
    final earned = progress.stars.values.fold<int>(0, (sum, value) => sum + value);
    final possible = data.catalog.levels.length * 3;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 12, 18, 8),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(color: _cream, borderRadius: BorderRadius.circular(16), boxShadow: const [BoxShadow(color: Color(0x33422A11), blurRadius: 8, offset: Offset(0, 4))]),
                child: Padding(
                  padding: const EdgeInsets.all(4),
                  child: Image.asset(
                    'assets/images/mivehchin_brand_mark.png',
                    fit: BoxFit.contain,
                    semanticLabel: 'نشان میوه‌چین',
                  ),
                ),
              ),
              const SizedBox(width: 11),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'باغ میوه‌چین',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: _brown, fontSize: 20, fontWeight: FontWeight.w900),
                    ),
                    Text(
                      'مسیر ماجراجویی',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: _leaf, fontWeight: FontWeight.w700),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
                decoration: BoxDecoration(color: _cream, borderRadius: BorderRadius.circular(14)),
                child: Text('⭐ ${_fa(earned)} / ${_fa(possible)}', style: const TextStyle(color: _brown, fontWeight: FontWeight.w900)),
              ),
              IconButton(
                tooltip: 'تنظیمات',
                onPressed: () => _openSettings(progress),
                icon: const Icon(Icons.settings_rounded, color: _brown),
              ),
              IconButton(
                tooltip: 'بازگشت به خانه',
                onPressed: () => Navigator.of(context).maybePop(),
                icon: const Icon(Icons.home_rounded, color: _brown),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 5),
          child: SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () => _openLevel(nextLevel),
              icon: const Icon(Icons.play_arrow_rounded),
              label: Text('ادامه از مرحلهٔ ${_fa(nextLevel.id)}'),
              style: FilledButton.styleFrom(backgroundColor: const Color(0xFFFFA43C), foregroundColor: Colors.white, padding: const EdgeInsets.symmetric(vertical: 14), textStyle: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
            ),
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.fromLTRB(18, 10, 18, 32),
            itemCount: data.catalog.levels.length,
            itemBuilder: (context, index) {
              final level = data.catalog.levels[index];
              final chestReward = mapChestRewards[level.id];
              final unlocked = level.id <= progress.lastUnlockedLevel;
              final stars = progress.starsFor(level.id);
              final left = index.isEven;
              final startsRegion = index == 0 ||
                  data.catalog.levels[index - 1].chapter != level.chapter;
              final regionLevels = data.catalog.levels
                  .where((candidate) => candidate.chapter == level.chapter)
                  .toList(growable: false);
              return Column(
                children: [
                  if (startsRegion)
                    _ChapterMarker(
                      title: level.chapterTitle,
                      firstLevel: regionLevels.first.id,
                      lastLevel: regionLevels.last.id,
                      unlocked: progress.lastUnlockedLevel >= regionLevels.first.id,
                    ),
                  SizedBox(
                    height: 82,
                    child: Stack(
                      children: [
                        if (index < data.catalog.levels.length - 1)
                          Positioned(
                            top: 52,
                            bottom: 0,
                            left: left ? 40 : null,
                            right: left ? null : 40,
                            child: Container(width: 4, color: const Color(0x66FFF5DA)),
                          ),
                        Align(
                          alignment: left ? Alignment.centerLeft : Alignment.centerRight,
                          child: _LevelNode(
                            level: level,
                            stars: stars,
                            unlocked: unlocked,
                            current: level.id == progress.lastUnlockedLevel,
                            onTap: unlocked ? () => _openLevel(level) : null,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (chestReward != null && stars > 0)
                    Align(
                      alignment: left ? Alignment.centerRight : Alignment.centerLeft,
                      child: MapChestMarker(
                        levelId: level.id,
                        reward: chestReward,
                        opened: data.economy.openedChestIds.contains('grove-milestone-${level.id}'),
                        enabled: widget.rewardedAds?.config.supports(RewardedUseCase.mapChest) ?? false,
                        onTap: () => _openChest(level.id, chestReward),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ChapterMarker extends StatelessWidget {
  const _ChapterMarker({
    required this.title,
    required this.firstLevel,
    required this.lastLevel,
    required this.unlocked,
  });

  final String title;
  final int firstLevel;
  final int lastLevel;
  final bool unlocked;

  @override
  Widget build(BuildContext context) {
    final foreground = unlocked ? _brown : const Color(0xFF686B60);
    return Padding(
      padding: const EdgeInsets.fromLTRB(2, 12, 2, 5),
      child: Semantics(
        key: ValueKey<String>('chapter-marker-$firstLevel'),
        label: unlocked ? '$title، منطقه باز' : '$title، منطقه قفل است',
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
          decoration: BoxDecoration(
            color: unlocked ? _cream.withValues(alpha: 0.94) : const Color(0x44FFFFFF),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: const Color(0x88FFFFFF)),
          ),
          child: Row(
            children: [
              if (unlocked)
                const GuideCharacter(
                  mood: OrchardGirlMood.encourage,
                  height: 34,
                )
              else
                Icon(Icons.lock_rounded, size: 19, color: foreground),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(color: foreground, fontWeight: FontWeight.w900),
                ),
              ),
              Text(
                'مرحلهٔ ${_fa(firstLevel)} تا ${_fa(lastLevel)}',
                style: TextStyle(color: foreground.withValues(alpha: 0.82), fontSize: 11),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LevelNode extends StatelessWidget {
  const _LevelNode({
    required this.level,
    required this.stars,
    required this.unlocked,
    required this.current,
    required this.onTap,
  });

  final LevelDefinition level;
  final int stars;
  final bool unlocked;
  final bool current;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final color = !unlocked
        ? const Color(0xFF9C9B8C)
        : current
            ? const Color(0xFFFFA43C)
            : stars > 0
                ? const Color(0xFFBFE2A4)
                : const Color(0xFFF9E9C9);
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: reduceMotion ? Duration.zero : Duration(milliseconds: 360 + (level.id % 5) * 42),
      curve: Curves.easeOutBack,
      builder: (context, value, child) {
        final progress = value.clamp(0.0, 1.0).toDouble();
        return Opacity(
          opacity: progress,
          child: Transform.translate(
            offset: Offset(0, 14 * (1 - progress)),
            child: Transform.scale(
              scale: 0.84 + 0.16 * progress,
              child: child,
            ),
          ),
        );
      },
      child: Semantics(
        key: ValueKey<String>('level-node-${level.id}'),
        button: true,
        enabled: unlocked,
        label: unlocked
            ? 'مرحله ${_fa(level.id)}، ${_fa(stars)} ستاره'
            : 'مرحله ${_fa(level.id)} قفل است',
        child: InkWell(
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              AnimatedContainer(
                duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 260),
                curve: Curves.easeOutCubic,
                width: 54,
                height: 54,
                decoration: BoxDecoration(
                  color: color,
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 3),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0x663C4A26),
                      blurRadius: current ? 13 : 7,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Center(
                  child: unlocked
                      ? Text(
                          _fa(level.id),
                          style: const TextStyle(
                            color: _brown,
                            fontSize: 19,
                            fontWeight: FontWeight.w900,
                          ),
                        )
                      : const Icon(Icons.lock_rounded, color: Colors.white),
                ),
              ),
              SizedBox(
                height: 19,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: List.generate(
                    3,
                    (index) => AnimatedSwitcher(
                      duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 220),
                      transitionBuilder: (child, animation) => FadeTransition(
                        opacity: animation,
                        child: ScaleTransition(scale: animation, child: child),
                      ),
                      child: Icon(
                        Icons.star_rounded,
                        key: ValueKey<bool>(index < stars),
                        size: 15,
                        color: index < stars
                            ? const Color(0xFFFFD54F)
                            : const Color(0x66FFF5DA),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MapData {
  const _MapData(this.catalog, this.progress, this.economy);
  final LevelCatalog catalog;
  final GameProgress progress;
  final EconomyProgress economy;
}

String _fa(int value) {
  const digits = '۰۱۲۳۴۵۶۷۸۹';
  return value.toString().split('').map((digit) {
    final number = int.tryParse(digit);
    return number == null ? digit : digits[number];
  }).join();
}
