import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../../game/models/economy_progress.dart';
import '../../../game/models/game_progress.dart';
import '../../../game/models/level_definition.dart';
import '../../../game/services/economy_repository.dart';
import '../../../game/services/level_catalog.dart';
import '../../../game/services/progress_repository.dart';
import '../../../game/services/rewarded_ad_service.dart';
import '../economy/economy_screen.dart';
import '../gem_match/gem_match_game.dart';
import '../../widgets/guide_character/guide_character.dart';
import '../../widgets/orchard_girl.dart';
import '../level_map/level_map_screen.dart';

const _leaf = Color(0xFF315D3E);
const _brown = Color(0xFF573B28);
const _cream = Color(0xFFFFF3DD);
const _orange = Color(0xFFF49A36);

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    super.key,
    this.catalogLoader,
    this.progressLoader,
    this.economyLoader,
    this.randomSeed,
  });

  final Future<LevelCatalog> Function()? catalogLoader;
  final Future<GameProgress> Function()? progressLoader;
  final Future<EconomyProgress> Function()? economyLoader;

  /// Optional deterministic board seed for integration-test level fixtures.
  final int? randomSeed;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final ProgressRepository _progressRepository = ProgressRepository();
  final EconomyRepository _economyRepository = EconomyRepository();
  late final RewardedAdService _rewardedAds = RewardedAdService(
    config: RewardedAdConfig.fromBuildMode(debugBuild: kDebugMode),
  );
  late Future<_HomeData> _data;

  @override
  void initState() {
    super.initState();
    _data = _load();
  }

  Future<_HomeData> _load() async {
    final values = await Future.wait<Object>([
      widget.catalogLoader?.call() ?? LevelCatalog.load(),
      widget.progressLoader?.call() ?? _progressRepository.load(),
      widget.economyLoader?.call() ?? _economyRepository.load(),
    ]);
    final progress = values[1] as GameProgress;
    if (progress.rewardedAdsConsent) await _rewardedAds.setConsent(true);
    return _HomeData(
      values[0] as LevelCatalog,
      progress,
      values[2] as EconomyProgress,
    );
  }

  @override
  void dispose() {
    unawaited(_rewardedAds.dispose());
    super.dispose();
  }

  Future<void> _continueGame(_HomeData data) async {
    final progress = await _progressRepository.setLastPlayedLevel(
      data.progress.lastPlayedLevel,
    );
    final level = data.catalog.byId(progress.lastPlayedLevel) ??
        data.catalog.levels.first;
    if (!mounted) return;
    await _openLevel(level, progress);
  }

  Future<void> _openLevel(LevelDefinition level, GameProgress progress) async {
    final economy = await _economyRepository.load();
    if (!mounted) return;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
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
          onRequestExtraMoves: (levelId) async {
            if (!_rewardedAds.consentGranted) {
              final initial = await _progressRepository.load();
              if (!mounted) return false;
              await _openSettings(initial);
              if (!_rewardedAds.consentGranted) return false;
            }
            final economy = await _economyRepository.load();
            if (economy.extraMoveLevels.contains(levelId)) return false;
            final claimId = 'extra-moves:level:$levelId';
            final result = await _rewardedAds.show(
              useCase: RewardedUseCase.extraMoves,
              claimId: claimId,
              onEarned: (id) async {
                final granted = await _economyRepository.claimExtraMoves(
                  levelId: levelId,
                  claimId: id,
                );
                if (!granted) throw StateError('Extra moves already granted for level $levelId.');
              },
            );
            return result.earned;
          },

        ),
      ),
    );
    if (mounted) {
      setState(() {
        _data = _load();
      });
    }
  }

  Future<void> _openSettings(GameProgress initial) async {
    var soundEnabled = initial.soundEnabled;
    var hapticsEnabled = initial.hapticsEnabled;
    var rewardedAdsConsent = initial.rewardedAdsConsent;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: _cream,
          title: const Text(
            'تنظیمات بازی',
            style: TextStyle(color: _brown, fontWeight: FontWeight.w900),
          ),
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
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('تبلیغ‌های جایزه‌دار Adivery', style: TextStyle(color: _brown)),
                subtitle: Text(
                  _rewardedAds.config.isConfigured
                      ? 'اختیاری است؛ با فعال‌سازی، SDK ادیوری برای نمایش تبلیغ آماده می‌شود.'
                      : 'شناسهٔ تبلیغ برای این نسخه تنظیم نشده؛ بازی آفلاین کار می‌کند.',
                  style: const TextStyle(color: _brown, fontSize: 12),
                ),
                value: rewardedAdsConsent && _rewardedAds.config.isConfigured,
                onChanged: _rewardedAds.config.isConfigured
                    ? (value) async {
                        setDialogState(() => rewardedAdsConsent = value);
                        await _progressRepository.setSettings(rewardedAdsConsent: value);
                        await _rewardedAds.setConsent(value);
                        if (mounted) {
                          setState(() {
                            _data = _load();
                          });
                        }
                      }
                    : null,
              ),
              if (_rewardedAds.config.isConfigured)
                const Padding(
                  padding: EdgeInsetsDirectional.only(start: 16, top: 4),
                  child: Text(
                    'Adivery ممکن است برای ارائهٔ تبلیغ داده‌های فنی و شناسه‌های تبلیغاتی دستگاه را پردازش کند. خاموش‌کردن، درخواست‌های بعدی بازی را متوقف می‌کند؛ برای توقف کامل SDK برنامه را دوباره باز کنید.',
                    style: TextStyle(color: _brown, fontSize: 11),
                  ),
                ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('بستن'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openEconomy() async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => EconomyScreen(
          repository: _economyRepository,
          rewardedAds: _rewardedAds,
          onRequestConsent: () async {
            final progress = await _progressRepository.load();
            if (mounted) await _openSettings(progress);
          },
        ),
      ),
    );
    if (mounted) {
      setState(() {
        _data = _load();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          Image.asset(
            'assets/orchard_match_background.png',
            fit: BoxFit.cover,
            alignment: Alignment.center,
          ),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Color(0x44223C27),
                  Color(0x33223C27),
                  Color(0xBB1D3825),
                ],
              ),
            ),
          ),
          SafeArea(
            child: FutureBuilder<_HomeData>(
              future: _data,
              builder: (context, snapshot) {
                if (snapshot.hasError) {
                  return Center(
                    child: Text(
                      'بارگذاری باغ ناموفق بود\\n${snapshot.error}',
                      textAlign: TextAlign.center,
                    ),
                  );
                }
                if (!snapshot.hasData) {
                  return const Center(
                    child: CircularProgressIndicator(color: _cream),
                  );
                }
                return _buildHome(snapshot.data!);
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHome(_HomeData data) {
    final progress = data.progress;
    final nextLevel = data.catalog.byId(progress.lastPlayedLevel) ??
        data.catalog.levels.first;
    final stars = progress.stars.values.fold<int>(0, (sum, value) => sum + value);
    final maxStars = data.catalog.levels.length * 3;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(22, 14, 22, 28),
          child: Column(
            children: [
              Align(
                alignment: AlignmentDirectional.centerEnd,
                child: IconButton.filledTonal(
                  tooltip: 'تنظیمات',
                  onPressed: () => _openSettings(progress),
                  style: IconButton.styleFrom(
                    backgroundColor: _cream.withValues(alpha: 0.92),
                    foregroundColor: _brown,
                  ),
                  icon: const Icon(Icons.settings_rounded),
                ),
              ),
              const SizedBox(height: 2),
              Container(
                width: 154,
                height: 154,
                padding: const EdgeInsets.all(9),
                decoration: BoxDecoration(
                  color: _cream.withValues(alpha: 0.92),
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 3),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x5525371D),
                      blurRadius: 22,
                      offset: Offset(0, 9),
                    ),
                  ],
                ),
                child: Image.asset(
                  'assets/images/mivehchin_brand_mark.png',
                  fit: BoxFit.contain,
                  semanticLabel: 'نشان سبد میوهٔ میوه‌چین',
                ),
              ),
              const SizedBox(height: 10),
              const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  GuideCharacter(
                    mood: OrchardGirlMood.happy,
                    height: 98,
                  ),
                  SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'میوه‌چین',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 38,
                            height: 1.1,
                            fontWeight: FontWeight.w900,
                            shadows: [
                              Shadow(
                                color: Color(0xAA27402B),
                                blurRadius: 12,
                                offset: Offset(0, 3),
                              ),
                            ],
                          ),
                        ),
                        SizedBox(height: 4),
                        Text(
                          'یک باغ جادویی، هزار جور میوه!',
                          style: TextStyle(
                            color: Color(0xFFFFF3D7),
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            shadows: [
                              Shadow(color: Color(0xAA27402B), blurRadius: 8),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
                decoration: BoxDecoration(
                  color: _cream.withValues(alpha: 0.94),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: Colors.white, width: 1.5),
                  boxShadow: const [
                    BoxShadow(color: Color(0x3325371D), blurRadius: 12, offset: Offset(0, 5)),
                  ],
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: _HomeStat(
                        icon: Icons.flag_rounded,
                        label: 'مرحلهٔ باز',
                        value: _fa(progress.lastUnlockedLevel),
                      ),
                    ),
                    Container(width: 1, height: 38, color: const Color(0xFFD8C7AB)),
                    Expanded(
                      child: _HomeStat(
                        icon: Icons.star_rounded,
                        label: 'ستاره‌ها',
                        value: '${_fa(stars)} / ${_fa(maxStars)}',
                      ),
                    ),
                    Container(width: 1, height: 38, color: const Color(0xFFD8C7AB)),
                    Expanded(
                      child: _HomeStat(
                        icon: Icons.monetization_on_rounded,
                        label: 'سکه',
                        value: _fa(data.economy.coins),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 17),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: () => _continueGame(data),
                  icon: const Icon(Icons.play_arrow_rounded, size: 29),
                  label: Text(
                    'ادامه از مرحلهٔ ${_fa(nextLevel.id)}',
                    style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                  ),
                  style: FilledButton.styleFrom(
                    backgroundColor: _orange,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
                    elevation: 5,
                  ),
                ),
              ),
              const SizedBox(height: 11),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () => Navigator.of(context).push<void>(
                    MaterialPageRoute<void>(
                      builder: (_) => LevelMapScreen(
                        catalog: data.catalog,
                        initialProgress: data.progress,
                        randomSeed: widget.randomSeed,
                        initialEconomy: data.economy,
                        rewardedAds: _rewardedAds,
                        economyRepository: _economyRepository,
                        onRequestConsent: () async {
                          final progress = await _progressRepository.load();
                          if (mounted) await _openSettings(progress);
                        },
                      ),
                    ),
                  ).then((_) {
                    if (!mounted) return;
                    setState(() {
                      _data = _load();
                    });
                  }),
                  icon: const Icon(Icons.map_rounded),
                  label: const Text('دیدن نقشهٔ مراحل'),
                  style: OutlinedButton.styleFrom(
                    backgroundColor: _cream.withValues(alpha: 0.92),
                    foregroundColor: _leaf,
                    side: const BorderSide(color: Colors.white, width: 1.5),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(17)),
                    textStyle: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15),
                  ),
                ),
              ),
              const SizedBox(height: 9),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: _openEconomy,
                  icon: const Icon(Icons.card_giftcard_rounded),
                  label: const Text('جایزه‌ها و فروشگاه باغ'),
                  style: OutlinedButton.styleFrom(
                    backgroundColor: _cream.withValues(alpha: 0.92),
                    foregroundColor: _leaf,
                    side: const BorderSide(color: Colors.white, width: 1.5),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(17)),
                    textStyle: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'هر جور، یک قدم نزدیک‌تر به باغ‌های تازه',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Color(0xFFFFF3D7),
                  fontWeight: FontWeight.w700,
                  fontSize: 12,
                  shadows: [Shadow(color: Color(0xAA27402B), blurRadius: 6)],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HomeStat extends StatelessWidget {
  const _HomeStat({required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Icon(icon, color: _leaf, size: 22),
          const SizedBox(height: 3),
          Text(label, style: const TextStyle(color: _brown, fontSize: 11)),
          const SizedBox(height: 2),
          Text(
            value,
            style: const TextStyle(color: _brown, fontSize: 16, fontWeight: FontWeight.w900),
          ),
        ],
      );
}

class _HomeData {
  const _HomeData(this.catalog, this.progress, this.economy);

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
