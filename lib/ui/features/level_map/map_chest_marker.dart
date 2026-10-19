import 'package:flutter/material.dart';

import '../../../game/models/economy_progress.dart';
import 'map_chests.dart';

const _chestBrown = Color(0xFF68492F);
const _chestCream = Color(0xFFFFF2DE);

class MapChestMarker extends StatelessWidget {
  const MapChestMarker({
    required this.levelId,
    required this.reward,
    required this.opened,
    required this.enabled,
    required this.onTap,
    super.key,
  });

  final int levelId;
  final MapChestReward reward;
  final bool opened;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final canOpen = enabled && !opened;
    final boosterName = switch (reward.booster) {
      BoosterType.hammer => 'چکش',
      BoosterType.shuffle => 'چیدمان دوباره',
      BoosterType.extraMoves => 'حرکت اضافه',
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Semantics(
        key: ValueKey<String>('map-chest-$levelId'),
        button: canOpen,
        enabled: canOpen,
        label: opened
            ? 'صندوق مرحلهٔ $levelId دریافت شده'
            : 'صندوق مرحلهٔ $levelId، ${reward.coins} سکه و $boosterName',
        child: Card(
          color: _chestCream,
          child: InkWell(
            onTap: canOpen ? onTap : null,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(opened ? Icons.inventory_2_rounded : Icons.card_giftcard_rounded,
                      color: opened ? _chestBrown.withValues(alpha: 0.5) : _chestBrown),
                  const SizedBox(width: 7),
                  Text(
                    opened
                        ? 'صندوق دریافت شد'
                        : 'صندوق · ${reward.coins} سکه + $boosterName',
                    style: TextStyle(
                      color: _chestBrown,
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      decoration: opened ? TextDecoration.none : null,
                    ),
                  ),
                  if (!opened && !enabled) ...[
                    const SizedBox(width: 6),
                    const Icon(Icons.lock_outline_rounded, size: 15, color: _chestBrown),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
