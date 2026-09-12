package org.voxelhorizons.platform.v1_12;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

final class LegacyNbtIdentity {

    private static final String CONTENT_ID_KEY = "VoxelCoreContentId";
    private static volatile Reflection cachedReflection;

    private LegacyNbtIdentity() {
    }

    static ItemStack write(ItemStack stack, String contentId) {
        try {
            Reflection reflection = reflection();
            Object nmsStack = reflection.asNmsCopy.invoke(null, stack);
            Object tag = reflection.getTag.invoke(nmsStack);

            if (tag == null) tag = reflection.nbtCompoundClass.newInstance();

            reflection.setString.invoke(tag, CONTENT_ID_KEY, contentId);
            reflection.setTag.invoke(nmsStack, tag);
            return (ItemStack) reflection.asBukkitCopy.invoke(null, nmsStack);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write VoxelCore legacy NBT identity", exception);
        }
    }

    static String read(ItemStack stack) {
        try {
            Reflection reflection = reflection();
            Object nmsStack = reflection.asNmsCopy.invoke(null, stack);
            Object tag = reflection.getTag.invoke(nmsStack);

            if (tag == null) return null;

            String value = (String) reflection.getString.invoke(tag, CONTENT_ID_KEY);
            return value == null || value.isEmpty() ? null : value;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read VoxelCore legacy NBT identity", exception);
        }
    }

    private static Reflection reflection() throws Exception {
        Reflection value = cachedReflection;
        if (value != null) return value;
        synchronized (LegacyNbtIdentity.class) {
            value = cachedReflection;
            if (value == null) {
                value = Reflection.create();
                cachedReflection = value;
            }
        }
        return value;
    }

    private static final class Reflection {
        private final Class<?> nbtCompoundClass;
        private final Method asNmsCopy;
        private final Method asBukkitCopy;
        private final Method getTag;
        private final Method setTag;
        private final Method setString;
        private final Method getString;

        private Reflection(Class<?> nbtCompoundClass,
                           Method asNmsCopy,
                           Method asBukkitCopy,
                           Method getTag,
                           Method setTag,
                           Method setString,
                           Method getString) {
            this.nbtCompoundClass = nbtCompoundClass;
            this.asNmsCopy = asNmsCopy;
            this.asBukkitCopy = asBukkitCopy;
            this.getTag = getTag;
            this.setTag = setTag;
            this.setString = setString;
            this.getString = getString;
        }

        private static Reflection create() throws Exception {
            String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
            String revision = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
            String nmsPackage = "net.minecraft.server." + revision;

            Class<?> craftItemStackClass = Class.forName(craftPackage + ".inventory.CraftItemStack");
            Class<?> nmsItemStackClass = Class.forName(nmsPackage + ".ItemStack");
            Class<?> nbtCompoundClass = Class.forName(nmsPackage + ".NBTTagCompound");

            Method asNmsCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass);
            Method getTag = nmsItemStackClass.getMethod("getTag");
            Method setTag = nmsItemStackClass.getMethod("setTag", nbtCompoundClass);
            Method setString = nbtCompoundClass.getMethod("setString", String.class, String.class);
            Method getString = nbtCompoundClass.getMethod("getString", String.class);

            return new Reflection(nbtCompoundClass, asNmsCopy, asBukkitCopy, getTag, setTag, setString, getString);
        }
    }
}
