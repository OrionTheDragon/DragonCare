package com.dragoncare.event;

import com.dragoncare.DragonCare;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;



/**
 * Не даёт рамкам / картинам исчезать, когда они висят на «неполном» блоке
 * (плита, забор, листва) или — для декораций гильдий, расставленных из
 * схематика — буквально на воздухе.
 *
 * <p>Ванильный {@code BlockAttachedEntity.tick()} раз в 100 тиков вызывает
 * {@code canStayAttached()} и делает {@code discard()}, если та вернула
 * false. Ранее это пытались чинить миксинами, но в собранном production-jar'е
 * они вели себя ненадёжно, поэтому авторитетным (и единственным) фиксом служит
 * этот обработчик событий: если декорация НЕ прошла бы ванильную проверку
 * крепления, мы целиком отменяем её тик — ветка с {@code discard()} просто
 * не выполняется.</p>
 *
 * <p>Рамки/картины на настоящих сплошных стенах продолжают тикать как
 * обычно — {@code canStayAttached()} для них возвращает true, тик не
 * отменяется, поведение ванили не меняется. Поводки ({@code LeashKnotEntity})
 * не затрагиваются: таргет — {@link AbstractDecorationEntity}, то есть только
 * картины и предметные рамки.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class FrameKeeperEvents {

    private static final Logger DC_LOG = LoggerFactory.getLogger("dragoncare/FrameKeeper");
    private static long DC_KEPT = 0L;

    private FrameKeeperEvents() {
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractDecorationEntity decoration)) return;
        if (decoration.getWorld().isClient) return;
        if (decoration.isRemoved()) return;

        // Вмешиваемся только тогда, когда ваниль сама бы выбросила декорацию.
        boolean staysNaturally;
        try {
            staysNaturally = decoration.canStayAttached();
        } catch (Throwable t) {
            return; // неизвестный контракт — пусть решает ваниль
        }
        if (staysNaturally) return; // висит на сплошной стене — поведение ванили

        // Иначе ванильный tick() сделал бы discard() — отменяем тик целиком.
        event.setCanceled(true);

        // Логируем ПЕРВОЕ срабатывание (чтобы было видно, что обработчик жив)
        // и далее раз в 100 — без спама.
        long n = ++DC_KEPT;
        if (n == 1L || n % 100 == 0L) {
            DC_LOG.info("[FRAME-KEEPER] kept {} alive at {} (keptTicks={})",
                    decoration.getClass().getSimpleName(),
                    decoration.getBlockPos(), n);
        }
    }
}
