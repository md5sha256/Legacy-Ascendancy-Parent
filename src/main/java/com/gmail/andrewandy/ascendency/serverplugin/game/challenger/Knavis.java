package com.gmail.andrewandy.ascendency.serverplugin.game.challenger;

import com.gmail.andrewandy.ascendency.lib.game.data.IChallengerData;
import com.gmail.andrewandy.ascendency.lib.game.data.game.ChallengerDataImpl;
import com.gmail.andrewandy.ascendency.serverplugin.AscendencyServerPlugin;
import com.gmail.andrewandy.ascendency.serverplugin.api.ability.Ability;
import com.gmail.andrewandy.ascendency.serverplugin.api.ability.AbstractAbility;
import com.gmail.andrewandy.ascendency.serverplugin.api.challenger.AbstractChallenger;
import com.gmail.andrewandy.ascendency.serverplugin.api.challenger.Challenger;
import com.gmail.andrewandy.ascendency.serverplugin.api.challenger.ChallengerUtils;
import com.gmail.andrewandy.ascendency.serverplugin.api.rune.AbstractRune;
import com.gmail.andrewandy.ascendency.serverplugin.api.rune.PlayerSpecificRune;
import com.gmail.andrewandy.ascendency.serverplugin.api.rune.Rune;
import com.gmail.andrewandy.ascendency.serverplugin.game.util.LocationMark;
import com.gmail.andrewandy.ascendency.serverplugin.matchmaking.AscendencyServerEvent;
import com.gmail.andrewandy.ascendency.serverplugin.matchmaking.match.ManagedMatch;
import com.gmail.andrewandy.ascendency.serverplugin.matchmaking.match.SimplePlayerMatchManager;
import com.gmail.andrewandy.ascendency.serverplugin.matchmaking.match.engine.GameEngine;
import com.gmail.andrewandy.ascendency.serverplugin.matchmaking.match.engine.GamePlayer;
import com.gmail.andrewandy.ascendency.serverplugin.util.Common;
import com.gmail.andrewandy.ascendency.serverplugin.util.game.Tickable;
import com.gmail.andrewandy.ascendency.serverplugin.util.keybind.ActiveKeyPressedEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.manipulator.mutable.PotionEffectData;
import org.spongepowered.api.data.manipulator.mutable.entity.HealthData;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.effect.potion.PotionEffectType;
import org.spongepowered.api.effect.potion.PotionEffectTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.entity.ChangeEntityPotionEffectEvent;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.item.inventory.ChangeInventoryEvent;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Represents the Knavis challenger. All abiliities and runes for Knavis can be found here.
 * //TODO test in game!
 */
public class Knavis extends AbstractChallenger implements Challenger {

    private static final Knavis instance = new Knavis();

    private Knavis() {
        super("Knavis",
                new Ability[]{ShadowsRetreat.instance, LivingGift.instance}, //Abilities
                new PlayerSpecificRune[]{ChosenOTEarth.instance, HeartOfTheDryad.instance, BlessingOfTeleportation.instance}, //Runes
                Challengers.getLoreOf("Knavis")); //Lore
    }

    public static Knavis getInstance() {
        return instance;
    }

    @Override
    public IChallengerData toData() {
        try {
            return new ChallengerDataImpl(getName(), new File("Path to file on server"), getLore());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create ChampionData", ex);
        }
    }

    public static class LivingGift implements Ability {

        private static LivingGift instance = new LivingGift();
        private Map<UUID, Integer> hitHistory = new HashMap<>();

        private LivingGift() {

        }

        public static LivingGift getInstance() {
            return instance;
        }

        @Override
        public boolean isPassive() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public String getName() {
            return "LivingGift";
        }

        @Listener
        public void onDamage(DamageEntityEvent event) {
            Collection<Player> players = event.getCause().allOf(Player.class);
            for (Player player : players) {
                if (!hitHistory.containsKey(player.getUniqueId())) {
                    continue;
                }
                int hits = hitHistory.get(player.getUniqueId());
                if (hits++ == 3) {
                    HealthData data = player.getHealthData();
                    data.set(data.health().transform((Double val) -> val + 3.0)); //Add 3 health or 1.5 hearts.
                    player.offer(data); //Update the player object.
                    hits = 0;
                    new LivingGiftUseEvent(player).callEvent();
                }
                hitHistory.replace(player.getUniqueId(), hits); //Update hit count
            }
        }

        private class LivingGiftUseEvent extends AscendencyServerEvent {

            private Cause cause;

            public LivingGiftUseEvent(Player player) {
                this.cause = Cause.builder().named("Player", player).build();
            }

            @Override
            public Cause getCause() {
                return cause;
            }
        }
    }

    public static class ShadowsRetreat extends AbstractAbility implements Tickable {

        public static final Long[] defaultTickThreshold = new Long[]{Common.toTicks(6, TimeUnit.SECONDS), Common.toTicks(6, TimeUnit.SECONDS)};
        private static final ShadowsRetreat instance = new ShadowsRetreat();
        private UUID uuid = UUID.randomUUID();
        private Map<UUID, LocationMark> dataMap = new HashMap<>();
        private Map<UUID, Integer> castCounter = new HashMap<>();
        private BiFunction<UUID, LocationMark, Long[]> tickThresholdFunction;
        private BiConsumer<Player, Integer> onMark;

        private ShadowsRetreat() {
            super("Shadow's Retreat", true);
        }

        public static ShadowsRetreat getInstance() {
            return instance;
        }

        public void setTickThresholdSupplier(BiFunction<UUID, LocationMark, Long[]> tickThresholdFunction) {
            this.tickThresholdFunction = tickThresholdFunction;
        }

        public void setOnMark(BiConsumer<Player, Integer> onMark) {
            this.onMark = onMark;
        }

        public Optional<LocationMark> getMarkFor(UUID player) {
            if (dataMap.containsKey(player)) {
                return Optional.of(dataMap.get(player));
            }
            return Optional.empty();
        }

        @Override
        public void tick() {
            dataMap.forEach((UUID player, LocationMark mark) -> {
                Long[] ticks = tickThresholdFunction == null ? defaultTickThreshold : tickThresholdFunction.apply(player, mark);
                //ticks is basically a long (tick threshold) for primary and secondary
                assert ticks.length == 2;
                if (mark.getPrimaryTick() >= ticks[0]) {
                    mark.setPrimaryMark(null);
                    mark.resetPrimaryTick();
                } else {
                    mark.incrementPrimary();
                }
                if (mark.getSecondaryTick() >= ticks[1]) {
                    mark.setPrimaryMark(null);
                    mark.resetSecondaryTick();
                } else {
                    mark.incrementSecondary();
                }
            });
        }

        @Override
        public UUID getUniqueID() {
            return uuid;
        }

        private void castAbilityAs(Player player) {
            if (!dataMap.containsKey(player.getUniqueId())) {
                throw new IllegalArgumentException("Player does not have this ability!");
            }
            LocationMark mark = dataMap.get(player.getUniqueId());
            castCounter.compute(player.getUniqueId(), (uuid, castCount) -> {
                if (castCount == null) {
                    castCount = 0;
                }
                if (castCount == 0) {
                    mark.setPrimaryMark(player.getLocation());
                    mark.resetPrimaryTick();
                } else {
                    MarkTeleportationEvent event = new MarkTeleportationEvent(player, mark.getPrimaryMark());
                    if (event.callEvent()) {
                        player.setLocationSafely(event.getTargetLocation());
                    }
                }
                return ++castCount;
            });

        }


        @Listener
        public void onHotbarChange(ChangeInventoryEvent.Held event) {
            Cause cause = event.getCause();
            Optional<Player> optionalPlayer = cause.allOf(UUID.class)
                    .parallelStream()
                    .map((uniqueID) -> Sponge.getServer().getPlayer(uniqueID))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findAny();
            if (!optionalPlayer.isPresent()) {
                return;
            }
            Player player = optionalPlayer.get();
            if (!dataMap.containsKey(player.getUniqueId())) {
                return;
            }
            Inventory inventory = player.getInventory();
            Optional<ItemStack> clicked = player.getItemInHand(HandTypes.MAIN_HAND);
            clicked.ifPresent((stack) -> {
                Optional<SlotIndex> index = inventory.getProperty(SlotIndex.class, SlotIndex.of(stack));
                index.ifPresent((SlotIndex slotIndex) -> {
                    assert slotIndex.getValue() != null;
                    if (onMark != null) {
                        onMark.accept(player, slotIndex.getValue());
                    }
                    if (slotIndex.getValue() != 2 || slotIndex.getValue() != 1) {
                        return;
                    }
                    castAbilityAs(player);
                });

            });
        }

        @Listener
        public void onActiveKeyPress(ActiveKeyPressedEvent event) {
            Player player = event.getPlayer();
            Optional<ManagedMatch> managedMatch = SimplePlayerMatchManager.INSTANCE.getMatchOf(player.getUniqueId());
            if (!managedMatch.isPresent()) {
                return;
            }
            ManagedMatch match = managedMatch.get();
            Optional<? extends GamePlayer> optional = match.getGamePlayerOf(player.getUniqueId());
            optional.ifPresent(gamePlayer -> {
                Challenger challenger = gamePlayer.getChallenger();
                if (challenger != Knavis.getInstance()) {
                    return;
                }
                //TODO call a mark given event so that runes can alter which slot the rune should go to.
            });
        }

        public static class LocationMarkedEvent extends AscendencyServerEvent {

            private final Player player;
            private int markSlot;

            public LocationMarkedEvent(Player marker, int markSlot) {
                this.player = marker;
                setMarkedSlot(markSlot);
            }

            public int getMarkedSlot() {
                return markSlot;
            }

            public void setMarkedSlot(int markSlot) {
                if (markSlot < 0 || markSlot > 9) {
                    throw new IllegalArgumentException("Invalid Mark Slot!");
                }
                this.markSlot = markSlot;
            }

            public Player getPlayer() {
                return player;
            }

            @Override
            public Cause getCause() {
                return null;
            }
        }

        private static class MarkTeleportationEvent extends AscendencyServerEvent implements Cancellable {

            private boolean cancel;
            private Player player;
            private Location<World> location;
            private Cause cause;

            public MarkTeleportationEvent(Player player, Location<World> toTeleport) {
                this.player = player;
                this.location = toTeleport;
                this.cause = Cause.builder().named("Knavis", Knavis.getInstance()).build();
            }

            public Location<World> getTargetLocation() {
                return location;
            }

            public void setTargetLocation(Location<World> location) {
                this.location = location;
            }

            public Player getPlayer() {
                return player;
            }

            @Override
            public Cause getCause() {
                return cause;
            }

            @Override
            public boolean isCancelled() {
                return cancel;
            }

            @Override
            public void setCancelled(boolean cancel) {
                this.cancel = cancel;
            }
        }
    }

    /**
     * Represents the rune BlessingOfTeleportation.
     */
    public static class BlessingOfTeleportation extends AbstractRune {

        private static final BlessingOfTeleportation instance = new BlessingOfTeleportation();
        private static final long ticks = Common.toTicks(8, TimeUnit.SECONDS);
        private Collection<UUID> active = new HashSet<>();

        private BlessingOfTeleportation() {
            ShadowsRetreat.instance.setTickThresholdSupplier( //Basically checks if they have this ability active, if so increase duration of marks to 8 sec
                    (UUID player, LocationMark mark) ->
                            active.contains(player) ? new Long[]{ticks, ticks} : ShadowsRetreat.defaultTickThreshold);
            ShadowsRetreat.instance.setOnMark((Player player, Integer slot) -> {
                assert slot != null;
                if (slot == 2) { //Don't need to care about 1 since it will be handled by Shadow's retreat.
                    Optional<LocationMark> mark = ShadowsRetreat.instance.getMarkFor(player.getUniqueId());
                    assert mark.isPresent();
                    LocationMark locationMark = mark.get();
                    locationMark.setSecondaryMark(player.getLocation());
                    locationMark.resetSecondaryTick();
                }
            });
        }

        public static BlessingOfTeleportation getInstance() {
            return instance;
        }

        @Override
        public void applyTo(Player player) {
            clearFrom(player);
            active.add(player.getUniqueId());
        }

        @Override
        public void clearFrom(Player player) {
            active.remove(player.getUniqueId());
            Optional<LocationMark> optional = ShadowsRetreat.getInstance().getMarkFor(player.getUniqueId());
            optional.ifPresent(LocationMark::clear);
        }

        @Override
        public String getName() {
            return "Blessing Of Teleportation";
        }

        @Override
        public void tick() {
            //This method does not actually need to tick since that is handled by the main ability
        }

        @Override
        public int getContentVersion() {
            return 0;
        }

        @Override
        public DataContainer toContainer() {
            return null;
        }
    }

    /**
     * Represents the rune HeartOfTheDryad
     */
    public static class HeartOfTheDryad extends AbstractRune {

        private static final HeartOfTheDryad instance = new HeartOfTheDryad();
        private Map<UUID, PotionEffect[]> registered = new HashMap<>();
        private Map<UUID, Long> currentActive = new HashMap<>();
        private Map<UUID, Long> cooldownMap = new HashMap<>();

        private HeartOfTheDryad() {
        }

        public static HeartOfTheDryad getInstance() {
            return instance;
        }

        @Override
        public void applyTo(Player player) {
            clearFrom(player);
            currentActive.put(player.getUniqueId(), 0L);
            Optional<PotionEffectData> optional = player.getOrCreate(PotionEffectData.class);
            if (!optional.isPresent()) {
                throw new IllegalStateException("Potion effect data could not be gathered for " + player.getUniqueId().toString());
            }

            PotionEffectData data = optional.get();
            PotionEffect[] effects = new PotionEffect[]{PotionEffect.builder()
                    //Level 2 movement speed
                    .potionType(PotionEffectTypes.SPEED)
                    .duration(4).amplifier(2).build(), PotionEffect.builder()
                    //20% Attack speed
                    .potionType(PotionEffectTypes.HASTE)
                    .duration(4).amplifier(2).build()};
            //Root / Entanglement
            for (PotionEffect effect : effects) {
                data.addElement(effect);
            }
            player.offer(data);
            registered.put(player.getUniqueId(), effects);
            Optional<ManagedMatch> optionalMatch = SimplePlayerMatchManager.INSTANCE.getMatchOf(player.getUniqueId());
            optionalMatch.ifPresent(managedMatch -> {
                GameEngine engine = managedMatch.getGameEngine();
                Optional<? extends GamePlayer> optionalPlayer = engine.getGamePlayerOf(player.getUniqueId());
                assert optionalPlayer.isPresent();
                GamePlayer gamePlayer = optionalPlayer.get();
                Collection<Rune> runes = gamePlayer.getRunes();
                runes.remove(this);
                runes.add(this);
            });
        }

        @Override
        public void clearFrom(Player player) {
            currentActive.remove(player.getUniqueId());
            cooldownMap.remove(player.getUniqueId());
            Optional<PotionEffectData> optional = player.getOrCreate(PotionEffectData.class);
            if (!optional.isPresent()) {
                throw new IllegalStateException("Potion effect data could not be gathered for " + player.getUniqueId().toString());
            }
            //Remove buffs from data
            PotionEffectData data = optional.get();
            PotionEffect[] effects = registered.get(player.getUniqueId());
            if (effects.length != 2) {
                return;
            }
            for (PotionEffect potionEffect : effects) {
                data.remove(potionEffect);
            }
            player.offer(data);
            registered.replace(player.getUniqueId(), new PotionEffect[0]);
            //If player is in a match, update the GamePlayer object
            Optional<ManagedMatch> optionalMatch = SimplePlayerMatchManager.INSTANCE.getMatchOf(player.getUniqueId());
            optionalMatch.ifPresent(managedMatch -> {
                GameEngine engine = managedMatch.getGameEngine();
                Optional<? extends GamePlayer> optionalPlayer = engine.getGamePlayerOf(player.getUniqueId());
                assert optionalPlayer.isPresent();
                GamePlayer gamePlayer = optionalPlayer.get();
                Collection<Rune> runes = gamePlayer.getRunes();
                runes.remove(this);
            });
        }

        /**
         * Reflects whether the player can have this rune applied.
         *
         * @param uuid The UUID of the player.
         * @return Returns whether the player can see noticable changes when the rune is "applied", checks
         * for if the player already has it or if they are on cooldown.
         */
        public boolean isEligible(UUID uuid) {
            return !currentActive.containsKey(uuid) && !cooldownMap.containsKey(uuid);
        }

        @Override
        public String getName() {
            return "Heart Of The Dryad";
        }

        /**
         * Updates the cooldowns and actives.
         */
        @Override
        public void tick() {
            cooldownMap.entrySet().removeIf(ChallengerUtils.mapTickPredicate(5L, TimeUnit.SECONDS, null));
            currentActive.entrySet().removeIf(ChallengerUtils.mapTickPredicate(4L, TimeUnit.SECONDS, (UUID uuid) -> {
                cooldownMap.put(uuid, 0L);
                registered.compute(uuid, (unused, unused1) -> new PotionEffect[0]); //If player is no longer active, remove his effects
            }));
        }

        @Override
        public int getContentVersion() {
            return 0;
        }

        @Override
        public DataContainer toContainer() {
            return null;
        }

        @Listener
        public void onPotionApplied(ChangeEntityPotionEffectEvent.Gain event) {
            //Check if the entity can have its this rune applied.
            if (!isEligible(event.getTargetEntity().getUniqueId())) {
                return;
            }
            PotionEffectType effect = event.getPotionEffect().getType();

            String name = effect.getName().toLowerCase();
            if (name.contains("fury") || effect == PotionEffectTypes.STRENGTH || effect == PotionEffectTypes.RESISTANCE) {
                assert event.getTargetEntity() instanceof Player;
                applyTo((Player) event.getTargetEntity());
            }
        }
    }

    /**
     * Represents Knavis' rune named "Chosen of the Earth"
     */
    public static class ChosenOTEarth extends AbstractRune {

        private static final ChosenOTEarth instance = new ChosenOTEarth();
        private Map<UUID, Integer> stacks = new HashMap<>();
        private Map<UUID, Long> tickHistory = new HashMap<>();

        private ChosenOTEarth() {
            Sponge.getEventManager().registerListeners(AscendencyServerPlugin.getInstance(), this);
        }

        public static ChosenOTEarth getInstance() {
            return instance;
        }

        @Override
        public void applyTo(Player player) {
            tickHistory.put(player.getUniqueId(), 0L);
        }

        @Override
        public void clearFrom(Player player) {
            tickHistory.remove(player.getUniqueId());
        }

        @Override
        public String getName() {
            return "Chosen of the Earth";
        }

        @Override
        public int getContentVersion() {
            return 0;
        }

        @Override
        public DataContainer toContainer() {
            return null;
        }

        /**
         * Handles when a player uses {@link LivingGift}
         */
        @Listener
        public void onGiftUse(LivingGift.LivingGiftUseEvent event) {
            Optional<Player> optionalPlayer = (event.getCause().get("Player", Player.class));
            assert optionalPlayer.isPresent();
            if (!tickHistory.containsKey(optionalPlayer.get().getUniqueId())) {
                return;
            }
            Player playerObj = optionalPlayer.get();
            tickHistory.replace(playerObj.getUniqueId(), 0L);
            stacks.compute(playerObj.getUniqueId(), ((UUID player, Integer stack) -> {
                int stackVal = stack == null ? 0 : stack; //Unboxing here may throw nullpointer.
                double health = 3;
                for (int index = 1; index < stackVal; ) {
                    health += index++;
                }
                Common.addHealth(playerObj, health - 3); //Sets the total health to a value between 3 and 7 (adds on to LivingGift)

                return stackVal == 4 ? stackVal : stackVal + 1; //If stack = 4, then max has been reached, therefore its 4 or stack + 1;
            }));
        }

        /**
         * Updates the stack history.
         */
        @Override
        public void tick() {
            tickHistory.entrySet().removeIf(ChallengerUtils.mapTickPredicate(6L, TimeUnit.SECONDS, stacks::remove));
        }
    }
}
