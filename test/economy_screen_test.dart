import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:puzzle/game/services/economy_repository.dart';
import 'package:puzzle/game/services/rewarded_ad_service.dart';
import 'package:puzzle/ui/features/economy/economy_screen.dart';

class _FakeClient implements RewardedAdClient {
  void Function(String)? onLoaded;
  void Function(String)? onShown;
  void Function(String, bool)? onClosed;
  void Function(String, String)? onError;
  final List<String> shown = <String>[];

  @override
  void setCallbacks({
    required void Function(String placement) onLoaded,
    required void Function(String placement) onShown,
    required void Function(String placement, bool isRewarded) onClosed,
    required void Function(String placement, String reason) onError,
  }) {
    this.onLoaded = onLoaded;
    this.onShown = onShown;
    this.onClosed = onClosed;
    this.onError = onError;
  }

  @override
  Future<void> initialize(String appId) async {}

  @override
  Future<bool> isLoaded(String placement) async => true;

  @override
  Future<void> prepare(String placement) async {
    onLoaded?.call(placement);
  }

  @override
  Future<void> show(String placement) async {
    shown.add(placement);
    onShown?.call(placement);
  }

  void close({required bool rewarded}) => onClosed?.call('reward-placement', rewarded);
}

RewardedAdService _service(_FakeClient client) => RewardedAdService(
      config: RewardedAdConfig(
        appId: 'demo-app',
        placements: {
          for (final useCase in RewardedUseCase.values) useCase: 'reward-placement',
        },
      ),
      client: client,
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('daily base reward is offline and cannot be claimed twice by reopening', (tester) async {
    final repository = EconomyRepository();
    final client = _FakeClient();
    final ads = _service(client);
    addTearDown(ads.dispose);

    await tester.pumpWidget(MaterialApp(
      home: EconomyScreen(repository: repository, rewardedAds: ads),
    ));
    await tester.pumpAndSettle();
    await tester.tap(find.text('دریافت ۵۰ سکه'));
    await tester.pumpAndSettle();
    expect((await repository.load()).coins, 150);
    expect(find.text('جایزهٔ امروز دریافت شد'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(MaterialApp(
      home: EconomyScreen(repository: EconomyRepository(), rewardedAds: ads),
    ));
    await tester.pumpAndSettle();
    expect(find.text('جایزهٔ امروز دریافت شد'), findsOneWidget);
    expect((await EconomyRepository().load()).coins, 150);
  });

  testWidgets('coin ad credits only after earned callback; close without reward pays zero', (tester) async {
    final repository = EconomyRepository();
    final client = _FakeClient();
    final ads = _service(client);
    addTearDown(ads.dispose);
    await ads.setConsent(true);

    await tester.pumpWidget(MaterialApp(
      home: EconomyScreen(repository: repository, rewardedAds: ads),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.textContaining('تماشای تبلیغ و دریافت'));
    await tester.pump();
    expect(client.shown, ['reward-placement']);
    client.close(rewarded: false);
    await tester.pumpAndSettle();
    expect((await repository.load()).coins, 100);
    expect(find.textContaining('چون پاداش تأیید نشد'), findsOneWidget);

    await tester.tap(find.textContaining('تماشای تبلیغ و دریافت'));
    await tester.pump();
    client.close(rewarded: true);
    await tester.pumpAndSettle();
    expect((await repository.load()).coins, 125);
    expect(find.text('پاداش با تأیید Adivery دریافت شد.'), findsOneWidget);
  });
}
