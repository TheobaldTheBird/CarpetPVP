package carpet.patches;

import carpet.CarpetSettings;
import carpet.mixins.LivingEntityAccessor;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import carpet.fakes.ServerPlayerInterface;
import carpet.utils.Messenger;

import java.io.Console;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("EntityConstructor")
public class EntityPlayerMPFake extends ServerPlayer
{
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final Set<String> spawning = new HashSet<>();

    public Runnable fixStartingPosition = () -> {};
    public boolean isAShadow;
    public Vec3 spawnPos;
    public double spawnYaw;

    // Returns true if it was successful, false if couldn't spawn due to the player not existing in Mojang servers
    public static boolean createFake(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying)
    {
        //prolly half of that crap is not necessary, but it works
        ServerLevel worldIn = server.getLevel(dimensionId);
        GameProfileCache.setUsesAuthentication(false);
        GameProfile gameprofile;
        try {
            gameprofile = server.getProfileCache().get(username).orElse(null); //findByName  .orElse(null)
        }
        finally {
            GameProfileCache.setUsesAuthentication(server.isDedicatedServer() && server.usesAuthentication());
        }
        if (gameprofile == null)
        {
            if (!CarpetSettings.allowSpawningOfflinePlayers)
            {
                return false;
            } else {
                gameprofile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(username), username);
            }
        }
        GameProfile finalGP = gameprofile;

        // We need to mark this player as spawning so that we do not
        // try to spawn another player with the name while the profile
        // is being fetched - preventing multiple players spawning
        String name = gameprofile.getName();
        spawning.add(name);

        fetchGameProfile(name).whenCompleteAsync((p, t) -> {
            // Always remove the name, even if exception occurs
            spawning.remove(name);
            if (t != null)
            {
                return;
            }

            GameProfile current = finalGP;
            if (p.isPresent())
            {
                current = p.get();
            }
            EntityPlayerMPFake instance = new EntityPlayerMPFake(server, worldIn, current, ClientInformation.createDefault(), false);
            instance.fixStartingPosition = () -> instance.snapTo(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);
            server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), instance, new CommonListenerCookie(current, 0, instance.clientInformation(), false));
            instance.teleportTo(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, true);
            instance.setHealth(20.0F);
            instance.unsetRemoved();
            instance.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
            instance.gameMode.changeGameModeForPlayer(gamemode);
            instance.spawnPos = pos;
            instance.spawnYaw = yaw;
            server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), dimensionId);//instance.dimension);
            server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), dimensionId);//instance.dimension);
            //instance.world.getChunkManager(). updatePosition(instance);
            instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f); // show all model layers (incl. capes)
            instance.getAbilities().flying = flying;
        }, server);
        return true;
    }

    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(final String name) {
        return SkullBlockEntity.fetchGameProfile(name);
    }

    public static EntityPlayerMPFake createShadow(MinecraftServer server, ServerPlayer player)
    {
        player.getServer().getPlayerList().remove(player);
        player.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
        ServerLevel worldIn = player.serverLevel();//.getWorld(player.dimension);
        GameProfile gameprofile = player.getGameProfile();
        EntityPlayerMPFake playerShadow = new EntityPlayerMPFake(server, worldIn, gameprofile, player.clientInformation(), true);
        playerShadow.setChatSession(player.getChatSession());
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), playerShadow, new CommonListenerCookie(gameprofile, 0, player.clientInformation(), true));

        playerShadow.setHealth(player.getHealth());
        playerShadow.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        playerShadow.gameMode.changeGameModeForPlayer(player.gameMode.getGameModeForPlayer());
        ((ServerPlayerInterface) playerShadow).getActionPack().copyFrom(((ServerPlayerInterface) player).getActionPack());
        // this might create problems if a player logs back in...
        playerShadow.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
        playerShadow.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, player.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));


        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(playerShadow, (byte) (player.yHeadRot * 256 / 360)), playerShadow.level().dimension());
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, playerShadow));
        //player.world.getChunkManager().updatePosition(playerShadow);
        playerShadow.getAbilities().flying = player.getAbilities().flying;
        return playerShadow;
    }

    public static EntityPlayerMPFake respawnFake(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation cli)
    {
        return new EntityPlayerMPFake(server, level, profile, cli, false);
    }

    public static boolean isSpawningPlayer(String username)
    {
        return spawning.contains(username);
    }

    private EntityPlayerMPFake(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli, boolean shadow)
    {
        super(server, worldIn, profile, cli);
        isAShadow = shadow;
    }

    @Override
    public void onEquipItem(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack)
    {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    @Override
    public void kill(ServerLevel level) {
        kill(Messenger.s("Killed"));
        DamageSource dmgSource = level.damageSources().fellOutOfWorld();
        die(dmgSource);
    }

    public void kill(Component reason) {
        shakeOff();

        if (reason.getContents() instanceof TranslatableContents text && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.onDisconnect(new DisconnectionDetails(reason));
        }
    }

    public void fakePlayerDisconnect(Component reason) {
        this.server.schedule(new TickTask(this.server.getTickCount(), () -> {
            this.connection.onDisconnect(new DisconnectionDetails(reason));
        }));

    }

    @Override
    public void tick() {
        if (this.getServer().getTickCount() % 10 == 0)
        {
            this.connection.resetPosition();
            this.serverLevel().getChunkSource().move(this);
        }
        try
        {
            super.tick();
            this.doTick();
        }
        catch (NullPointerException ignored)
        {
            // happens with that paper port thingy - not sure what that would fix, but hey
            // the game not gonna crash violently.
        }


    }

    private void shakeOff()
    {
        if (getVehicle() instanceof Player) stopRiding();
        for (Entity passenger : getIndirectPassengers())
        {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    @Override
    public void die(DamageSource cause) {
        shakeOff();
        super.die(cause);
        kill(this.getCombatTracker().getDeathMessage());
        this.executor.schedule(this::respawn, 1L, TimeUnit.MILLISECONDS);
        this.setHealth(20);
        this.foodData = new FoodData();
        giveExperienceLevels(-(experienceLevel + 1));
        kill(this.getCombatTracker().getDeathMessage());
        this.teleportTo(spawnPos.x, spawnPos.y, spawnPos.z);
        this.executor.schedule(() -> this.setDeltaMovement(0, 0, 0), 1L, TimeUnit.MILLISECONDS);
    }

//    public void respawn()
//    {
//        this.setHealth(20);
//        this.foodData = new FoodData();
//        this.teleportTo(spawnPos.x, spawnPos.y, spawnPos.z);
//        this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(serverLevel()), (byte)3));
//    }

    public void stop()
    {

    }

    @Override
    public String getIpAddress()
    {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        return CarpetSettings.allowListingFakePlayers;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        doCheckFallDamage(0.0, y, 0.0, onGround);
    }

    @Override
    public ServerPlayer teleport(TeleportTransition serverLevel) {
        super.teleport(serverLevel);
        if (wonGame) {
            ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
            connection.handleClientCommand(p);
        }

        // If above branch was taken, *this* has been removed and replaced, the new instance has been set
        // on 'our' connection (which is now theirs, but we still have a ref).
        if (connection.player.isChangingDimension()) {
            connection.player.hasChangedDimension();
        }
        return connection.player;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource) {
        return super.isInvulnerableTo(serverLevel, damageSource) || this.isChangingDimension() && !damageSource.is(DamageTypes.ENDER_PEARL) || !this.hasClientLoaded();
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
        // copy-pasted from LivingEntity with small changes
        if (this.gameMode.getGameModeForPlayer() == GameType.CREATIVE || this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return false;
        }
        if (this.isInvulnerableTo(serverLevel, damageSource)) {
            return false;
        } else if (this.isDeadOrDying()) {
            return false;
        } else if (damageSource.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            if (f < 0.0F) {
                f = 0.0F;
            }

            float g = f;
            float h = this.applyItemBlocking(serverLevel, damageSource, f);
            f -= h;
            boolean bl = h > 0.0F;
            if (damageSource.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                f *= 5.0F;
            }

            if (damageSource.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damageSource, f);
                f *= 0.75F;
            }

            if (Float.isNaN(f) || Float.isInfinite(f)) {
                f = Float.MAX_VALUE;
            }

            boolean bl2 = true;
            if ((float)this.invulnerableTime > 10.0F && !damageSource.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (f <= this.lastHurt) {
                    return false;
                }

                this.actuallyHurt(serverLevel, damageSource, f - this.lastHurt);
                this.lastHurt = f;
                bl2 = false;
            } else {
                this.lastHurt = f;
                this.invulnerableTime = 20;
                this.actuallyHurt(serverLevel, damageSource, f);
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            this.resolveMobResponsibleForDamage(damageSource);
            this.resolvePlayerResponsibleForDamage(damageSource);
            if (bl2) {
                BlocksAttacks blocksAttacks = (BlocksAttacks)this.getUseItem().get(DataComponents.BLOCKS_ATTACKS);
                if (bl && blocksAttacks != null) {
                    blocksAttacks.onBlocked(serverLevel, this);
                } else {
                    serverLevel.broadcastDamageEvent(this, damageSource);
                }

                if (!damageSource.is(DamageTypeTags.NO_IMPACT) && (!bl || f > 0.0F)) {
                    this.markHurt();
                }

                if (!damageSource.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double d = 0.0d;
                    double e = 0.0d;
                    Entity var14 = damageSource.getDirectEntity();
                    if (var14 instanceof Projectile) {
                        Projectile projectile = (Projectile)var14;
                        DoubleDoubleImmutablePair doubleDoubleImmutablePair = projectile.calculateHorizontalHurtKnockbackDirection(this, damageSource);
                        d = -doubleDoubleImmutablePair.leftDouble();
                        e = -doubleDoubleImmutablePair.rightDouble();
                    } else if (damageSource.getSourcePosition() != null) {
                        d = damageSource.getSourcePosition().x() - this.getX();
                        e = damageSource.getSourcePosition().z() - this.getZ();
                    }

                    // moved this.knockback into the if block instead of before it
                    if (!bl) {
                        this.knockback(0.4d, d, e);
                        this.indicateDamage(d, e);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                try {
                    if (!((LivingEntityAccessor) this).invokeCheckTotemDeathProtection(damageSource)) {
                        if (bl2) {
                            this.makeSound(this.getDeathSound());
                            ((LivingEntityAccessor) this).invokePlaySecondaryHurtSound(damageSource);
                        }

                        this.die(damageSource);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            } else if (bl2 && (!CarpetSettings.shieldStunning || !bl)) { // changed here
                this.playHurtSound(damageSource);
                ((LivingEntityAccessor) this).invokePlaySecondaryHurtSound(damageSource);
            }

            boolean bl3 = !bl || f > 0.0F;
            if (bl3) {
                ((LivingEntityAccessor) this).setLastDamageSource(damageSource);
                ((LivingEntityAccessor) this).setLastDamageStamp(this.level().getGameTime());

                for(MobEffectInstance mobEffectInstance : this.getActiveEffects()) {
                    mobEffectInstance.onMobHurt(serverLevel, this, damageSource, f);
                }
            }

            if (this instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)this;
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverPlayer, damageSource, g, f, bl);
                if (h > 0.0F && h < 3.4028235E37F) {
                    serverPlayer.awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(h * 10.0F));
                }
            }

            Entity var20 = damageSource.getEntity();
            if (var20 instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)var20;
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverPlayer, this, damageSource, g, f, bl);
            }

            return bl3;
        }
    }


    @Override
    protected void blockUsingItem(ServerLevel serverLevel, LivingEntity livingEntity) {
        // copy-pasted from LivingEntity with small changes

        // this would apply wrong knockback
//        super.blockUsingItem(serverLevel, livingEntity);
        ItemStack itemStack = this.getItemBlockingWith();
        BlocksAttacks blocksAttacks = itemStack != null ? (BlocksAttacks)itemStack.get(DataComponents.BLOCKS_ATTACKS) : null;
        float f = livingEntity.getSecondsToDisableBlocking();
        if (f > 0.0F && blocksAttacks != null) {
            blocksAttacks.disable(serverLevel, this, f, itemStack);
            this.invulnerableTime = 20;
            if (CarpetSettings.shieldStunning) { // correct behavior for shield stunning
                executor.schedule(() -> {
                    this.invulnerableTime = 0;
                }, 1, TimeUnit.MILLISECONDS);
            }
        }

    }
}