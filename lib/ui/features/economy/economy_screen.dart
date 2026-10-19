import 'dart:async';

import 'package:flutter/material.dart';

import '../../../game/models/economy_progress.dart';
import '../../../game/services/economy_repository.dart';
import '../../../game/services/rewarded_ad_service.dart';

const _green = Color(0xFF315D3E);
const _brown = Color(0xFF573B28);
const _cream = Color(0xFFFFF3DD);
const _gold = Color(0xFFF49A36);

class EconomyScreen extends StatefulWidget {
  const EconomyScreen({
    required this.repository,
    required this.rewardedAds,
    this.onRequestConsent,
    super.key,
  });

  final EconomyRepository repository;
  final RewardedAdService rewardedAds;
  final Future<void> Function()? onRequestConsent;

  @override
  State<EconomyScreen> createState() => _EconomyScreenState();
}

class _EconomyScreenState extends State<EconomyScreen> {
  static int _claimSerial = 0;

  late Future<EconomyProgress> _economy;
  StreamSubscription<RewardedAdSnapshot>? _adSubscription;
  RewardedAdState _adState = RewardedAdState.disabled;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _economy = widget.repository.load();
    _adState = widget.rewardedAds.state;
    _adSubscription = widget.rewardedAds.states.listen((snapshot) {
      if (mounted) setState(() => _adState = snapshot.state);
    });
  }

  @override
  void dispose() {
    unawaited(_adSubscription?.cancel());
    super.dispose();
  }

  String get _today {
    final now = DateTime.now();
    return '${now.year.toString().padLeft(4, '0')}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
  }

  String _newClaimId(String purpose) =>
      '$purpose:${DateTime.now().microsecondsSinceEpoch}:${_claimSerial++}';

  Future<void> _reload() async {
    final future = widget.repository.load();
    if (mounted) {
      setState(() {
        _economy = future;
      });
    }
    await future;
  }

  void _message(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _claimDailyReward() async {
    final claimed = await widget.repository.claimDailyReward(
      dayKey: _today,
      coins: RewardedAdAmounts.dailyBaseCoins,
    );
    await _reload();
    _message(claimed ? 'جایزهٔ روزانه به کیف سکه اضافه شد.' : 'جایزهٔ امروز قبلاً دریافت شده است.');
  }

  Future<void> _runRewardedAd({
    required RewardedUseCase useCase,
    required String claimId,
    required Future<void> Function(String claimId) grant,
  }) async {
    if (_busy) return;
    if (!widget.rewardedAds.config.supports(useCase)) {
      _message('تبلیغ برای این نسخه تنظیم نشده؛ بازی آفلاین همچنان در دسترس است.');
      return;
    }
    if (!widget.rewardedAds.consentGranted) {
      await widget.onRequestConsent?.call();
      if (!widget.rewardedAds.consentGranted) {
        _message('برای تبلیغ جایزه‌دار، ابتدا رضایت را در تنظیمات فعال کن.');
      }
      return;
    }

    setState(() => _busy = true);
    final result = await widget.rewardedAds.show(
      useCase: useCase,
      claimId: claimId,
      onEarned: grant,
    );
    await _reload();
    if (mounted) setState(() => _busy = false);
    _message(switch (result.outcome) {
      RewardedAdOutcome.earned => 'پاداش با تأیید Adivery دریافت شد.',
      RewardedAdOutcome.dismissed => 'تبلیغ بسته شد؛ چون پاداش تأیید نشد چیزی اضافه نشد.',
      RewardedAdOutcome.unavailable => 'تبلیغ آماده نیست یا اتصال در دسترس نیست؛ کمی بعد دوباره تلاش کن.',
      RewardedAdOutcome.failed => 'تبلیغ ناموفق بود؛ هیچ پاداشی ثبت نشد.',
      RewardedAdOutcome.alreadyClaimed => 'این پاداش قبلاً ثبت شده است.',
      RewardedAdOutcome.busy => 'یک تبلیغ دیگر هنوز در حال اجراست.',
    });
  }

  Future<void> _watchForCoins() async {
    final claimId = _newClaimId('coin-ad');
    await _runRewardedAd(
      useCase: RewardedUseCase.coinBonus,
      claimId: claimId,
      grant: (id) async {
        final granted = await widget.repository.grantRewardOnce(
          claimId: id,
          coins: RewardedAdAmounts.coinBonus,
        );
        if (!granted) throw StateError('Coin reward was already recorded.');
      },
    );
  }

  Future<void> _doubleDailyReward() async {
    final day = _today;
    await _runRewardedAd(
      useCase: RewardedUseCase.dailyDouble,
      claimId: 'daily-double:$day',
      grant: (id) async {
        final granted = await widget.repository.claimDailyDouble(
          dayKey: day,
          claimId: id,
        );
        if (!granted) throw StateError('Daily double was already recorded.');
      },
    );
  }

  Future<void> _watchForBooster(EconomyProgress progress) async {
    final day = _today;
    final usedToday = progress.freeBoosterDay == day ? progress.freeBoosterAdCount : 0;
    final claimId = 'free-hammer:$day:${usedToday + 1}';
    await _runRewardedAd(
      useCase: RewardedUseCase.freeBooster,
      claimId: claimId,
      grant: (id) async {
        final granted = await widget.repository.claimFreeBooster(
          dayKey: day,
          claimId: id,
          booster: BoosterType.hammer,
          dailyLimit: _safeBoosterDailyLimit,
        );
        if (!granted) throw StateError('Daily booster reward was already recorded.');
      },
    );
  }

  int get _safeBoosterDailyLimit =>
      RewardedAdAmounts.freeBoosterDailyLimit.clamp(0, 50).toInt();

  Future<void> _buyBooster(BoosterType booster, int price) async {
    final bought = await widget.repository.purchaseBooster(
      booster: booster,
      count: 1,
      cost: price,
    );
    await _reload();
    _message(bought ? 'تقویت‌کننده به کیف باغ اضافه شد.' : 'سکهٔ کافی برای این خرید نداری.');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: _cream,
        appBar: AppBar(
          title: const Text('جایزه‌ها و فروشگاه باغ'),
          backgroundColor: _green,
          foregroundColor: Colors.white,
        ),
        body: FutureBuilder<EconomyProgress>(
          future: _economy,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return const Center(child: Text('ذخیرهٔ کیف پول در دسترس نیست.'));
            }
            if (!snapshot.hasData) {
              return const Center(child: CircularProgressIndicator(color: _green));
            }
            return _buildContent(snapshot.data!);
          },
        ),
      );

  Widget _buildContent(EconomyProgress progress) {
    final claimedToday = progress.dailyRewardDay == _today;
    final doubledToday = progress.dailyDoubleDay == _today;
    final boosterUsed = progress.freeBoosterDay == _today ? progress.freeBoosterAdCount : 0;
    final canOfferBooster = _safeBoosterDailyLimit > 0 && boosterUsed < _safeBoosterDailyLimit;

    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 620),
        child: ListView(
          padding: const EdgeInsets.all(18),
          children: [
            _WalletCard(progress: progress),
            const SizedBox(height: 14),
            _SectionCard(
              icon: Icons.calendar_month_rounded,
              title: 'جایزهٔ روزانه',
              children: [
                Text(
                  'روزهای پیاپی: ${_fa(progress.dailyStreak)}',
                  style: const TextStyle(color: _brown, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: _busy || claimedToday ? null : _claimDailyReward,
                  icon: const Icon(Icons.card_giftcard_rounded),
                  label: Text(
                    claimedToday
                        ? 'جایزهٔ امروز دریافت شد'
                        : 'دریافت ${_fa(RewardedAdAmounts.dailyBaseCoins)} سکه',
                  ),
                  style: FilledButton.styleFrom(backgroundColor: _gold, foregroundColor: Colors.white),
                ),
                if (claimedToday && !doubledToday) ...[
                  const SizedBox(height: 7),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _doubleDailyReward,
                    icon: const Icon(Icons.ondemand_video_rounded),
                    label: Text('با تبلیغ ${_fa(progress.dailyRewardCoins)} سکهٔ دیگر بگیر'),
                  ),
                ],
                if (doubledToday)
                  const Text('بخش اضافهٔ جایزهٔ روزانه هم ثبت شده است.',
                      style: TextStyle(color: _green)),
              ],
            ),
            const SizedBox(height: 12),
            _SectionCard(
              icon: Icons.play_circle_fill_rounded,
              title: 'پاداش با تبلیغ اختیاری',
              children: [
                Text(_adStatusText, style: const TextStyle(color: _brown, fontSize: 13)),
                const SizedBox(height: 8),
                FilledButton.icon(
                  onPressed: _busy ? null : _watchForCoins,
                  icon: const Icon(Icons.monetization_on_rounded),
                  label: Text('تماشای تبلیغ و دریافت ${_fa(RewardedAdAmounts.coinBonus)} سکه'),
                ),
                if (canOfferBooster) ...[
                  const SizedBox(height: 6),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : () => _watchForBooster(progress),
                    icon: const Icon(Icons.hardware_rounded),
                    label: Text('یک چکش رایگان (${_fa(_safeBoosterDailyLimit - boosterUsed)} نوبت امروز)'),
                  ),
                ] else
                  Text('سقف روزانهٔ چکش رایگان (${_fa(_safeBoosterDailyLimit)}) تکمیل شده است.',
                      style: const TextStyle(color: _brown, fontSize: 12)),
              ],
            ),
            const SizedBox(height: 12),
            _SectionCard(
              icon: Icons.storefront_rounded,
              title: 'فروشگاه تقویت‌کننده‌ها',
              children: [
                const Text(
                  'هر تقویت‌کننده در نوار پایین صفحهٔ بازی مصرف می‌شود؛ حرکت اضافه در پنجرهٔ باخت هم در دسترس است.',
                  style: TextStyle(color: _brown, fontSize: 12),
                ),
                const SizedBox(height: 4),
                _ShopRow(
                  icon: Icons.hardware_rounded,
                  title: 'چکش',
                  count: progress.boostersOf(BoosterType.hammer),
                  price: 80,
                  onBuy: _busy ? null : () => _buyBooster(BoosterType.hammer, 80),
                ),
                _ShopRow(
                  icon: Icons.shuffle_rounded,
                  title: 'چیدمان دوباره',
                  count: progress.boostersOf(BoosterType.shuffle),
                  price: 60,
                  onBuy: _busy ? null : () => _buyBooster(BoosterType.shuffle, 60),
                ),
                _ShopRow(
                  icon: Icons.add_circle_rounded,
                  title: 'حرکت اضافه',
                  count: progress.boostersOf(BoosterType.extraMoves),
                  price: 100,
                  onBuy: _busy ? null : () => _buyBooster(BoosterType.extraMoves, 100),
                ),
              ],
            ),
            const SizedBox(height: 12),
            const Text(
              'کیف پول و جایزه‌ها روی همین دستگاه ذخیره می‌شوند. پاداش روزانه آفلاین است و با تغییر ساعت دستگاه قابل دست‌کاری است.',
              textAlign: TextAlign.center,
              style: TextStyle(color: _brown, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }

  String get _adStatusText {
    if (!widget.rewardedAds.config.isConfigured) {
      return 'شناسهٔ تبلیغ در این نسخه موجود نیست؛ همهٔ بخش‌های بازی آفلاین می‌مانند.';
    }
    if (!widget.rewardedAds.consentGranted) {
      return 'تبلیغ تا زمانی که در تنظیمات رضایت نداده‌ای بارگیری یا نمایش داده نمی‌شود.';
    }
    return switch (_adState) {
      RewardedAdState.loading => 'در حال آماده‌سازی تبلیغ…',
      RewardedAdState.loaded => 'تبلیغ جایزه‌دار آماده است.',
      RewardedAdState.showing => 'تبلیغ در حال نمایش است…',
      RewardedAdState.earned => 'پاداش تأیید شد.',
      RewardedAdState.dismissed => 'تبلیغ بدون دریافت پاداش بسته شد.',
      RewardedAdState.failed => 'تبلیغ در دسترس نیست؛ بازی را می‌توانی آفلاین ادامه بدهی.',
      RewardedAdState.idle => 'تبلیغ جایزه‌دار اختیاری است.',
      RewardedAdState.disabled => 'تبلیغ غیرفعال است.',
    };
  }
}

class _WalletCard extends StatelessWidget {
  const _WalletCard({required this.progress});

  final EconomyProgress progress;

  @override
  Widget build(BuildContext context) => Card(
        color: const Color(0xFFFFE7AE),
        elevation: 1,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 18),
          child: Row(
            children: [
              const Icon(Icons.monetization_on_rounded, color: _gold, size: 38),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('کیف سکه', style: TextStyle(color: _brown, fontWeight: FontWeight.w800)),
                    Text(_fa(progress.coins),
                        style: const TextStyle(color: _brown, fontWeight: FontWeight.w900, fontSize: 24)),
                  ],
                ),
              ),
              Text(
                'چکش ${_fa(progress.boostersOf(BoosterType.hammer))}\n'
                'جابه‌جایی ${_fa(progress.boostersOf(BoosterType.shuffle))}',
                textAlign: TextAlign.end,
                style: const TextStyle(color: _brown, fontSize: 12, fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ),
      );
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.icon, required this.title, required this.children});

  final IconData icon;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Card(
        color: Colors.white.withValues(alpha: 0.88),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(icon, color: _green),
                  const SizedBox(width: 8),
                  Text(title, style: const TextStyle(color: _brown, fontWeight: FontWeight.w900, fontSize: 17)),
                ],
              ),
              const SizedBox(height: 9),
              ...children,
            ],
          ),
        ),
      );
}

class _ShopRow extends StatelessWidget {
  const _ShopRow({
    required this.icon,
    required this.title,
    required this.count,
    required this.price,
    required this.onBuy,
  });

  final IconData icon;
  final String title;
  final int count;
  final int price;
  final VoidCallback? onBuy;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          children: [
            Icon(icon, color: _green),
            const SizedBox(width: 8),
            Expanded(
              child: Text('$title · ${_fa(count)}',
                  style: const TextStyle(color: _brown, fontWeight: FontWeight.w700)),
            ),
            TextButton.icon(
              onPressed: onBuy,
              icon: const Icon(Icons.monetization_on_rounded, size: 17),
              label: Text(_fa(price)),
            ),
          ],
        ),
      );
}

String _fa(int value) {
  const digits = '۰۱۲۳۴۵۶۷۸۹';
  return value.toString().split('').map((digit) {
    final number = int.tryParse(digit);
    return number == null ? digit : digits[number];
  }).join();
}
