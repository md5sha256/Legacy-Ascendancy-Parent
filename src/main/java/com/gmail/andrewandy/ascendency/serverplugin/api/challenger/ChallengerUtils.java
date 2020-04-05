package com.gmail.andrewandy.ascendency.serverplugin.api.challenger;

import com.gmail.andrewandy.ascendency.serverplugin.util.Common;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ChallengerUtils {

    /**
     * Increment the tick count of each individual element in a map.
     *
     * @param removeAfter The time in which the entry should be removed.
     * @param unit        The unit of time.
     * @param onRemove    An action to be done when an element is removed, can be null.
     * @return Returns a predicate to be used in {@link java.util.Collection#removeIf(Predicate)}
     */
    public static Predicate<Map.Entry<UUID, Long>> mapTickPredicate(long removeAfter, TimeUnit unit, Consumer<UUID> onRemove) {
        return mapTickPredicate(Common.toTicks(removeAfter, unit), onRemove);
    }

    /**
     * Increment the tick count of each individual element in a map.
     *
     * @param removeAfter The tick count in which the entry should be removed.
     * @param onRemove    An action to be done when an element is removed, can be null/
     * @return Returns a predicate to be used in {@link java.util.Collection#removeIf(Predicate)}
     */
    public static Predicate<Map.Entry<UUID, Long>> mapTickPredicate(long removeAfter, Consumer<UUID> onRemove) {
        return (Map.Entry<UUID, Long> entry) -> {
            entry.setValue(entry.getValue() + 1); //Increment tick count
            if (entry.getValue() >= removeAfter) {
                if (onRemove != null) onRemove.accept(entry.getKey());
                return true;
            }
            return false;
        }; //Clear if greater than the number of ticks in x seconds.
    }

    public static void teleportPlayer(Player player, double distance) {
        //player.setLocationSafely(new Location<>(player.getWorld(), player.getTransform().getRotation().mul(distance);));
        double yaw = player.getTransform().getYaw();
        double theta = yaw > 360 ? yaw % 360 : yaw;
        double x, z;
        Location<World> location = player.getLocation();
        x = distance * Math.cos(theta);
        z = distance * Math.sin(theta);
        player.setLocationSafely(new Location<>(location.getExtent(), x + location.getX(), location.getY(), z + location.getZ()));
    }

    /**
     * @param potionEffect
     * @return
     */
    public static boolean isEffectNegative(PotionEffect potionEffect) {
        return false;
    }
}
