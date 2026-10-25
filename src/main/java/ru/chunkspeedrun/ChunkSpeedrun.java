package ru.chunkspeedrun;

import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ProblemReporter;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

public class ChunkSpeedrun implements ModInitializer {
    public static final org.slf4j.Logger LOG = LoggerFactory.getLogger("ChunkSpeedrun");
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path GAME_DIR=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
    public static volatile Run run;
    public static MinecraftServer server;
    public static volatile boolean input;
    public static boolean restoring;
    public static boolean blocksChanged;
    private static long lastNano;
    private static boolean previousPaused = true;
    private static int ticks;
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"ChunkSpeedrun HTTP");t.setDaemon(true);return t;});
    public static volatile String[] verified = {}, unverified = {};
    public static volatile String networkStatus = "GitHub • загрузка таблиц…";
    public static class Run {
        public int schema=1, chunkX,chunkZ,minY,maxY,spawnX,spawnY,spawnZ,remaining;
        public long startedAt,activeNanos,finishedAt,initialDayTime;
        public int initialClear,initialRainTime,initialThunderTime;
        public boolean initialRain,initialThunder;
        public boolean finished;
        public String attemptId=UUID.randomUUID().toString(),nickname="";
        public double originalBorderSize,originalBorderX,originalBorderZ;
        public transient Path directory;
        public transient int[] snapshot;
        public transient CompoundTag snapshotNbt;
        public transient List<Display.TextDisplay> boards = new ArrayList<>();
        public boolean inside(BlockPos p){return p.getX()>>4==chunkX && p.getZ()>>4==chunkZ;}
    }
    public record Result(String attemptId,String nickname,long activeMillis,long totalMillis,String status,String rules) {}
    public static class Config {public String repository="Xvobuk/chunk-speedrun"; public String submissionEndpoint="";}
    public static Config config=new Config();
    @Override public void onInitialize(){
        try {Path p=GAME_DIR.resolve("config/chunkspeedrun.json");Files.createDirectories(p.getParent());if(Files.exists(p))config=JSON.fromJson(Files.readString(p),Config.class);else Files.writeString(p,JSON.toJson(config));}
        catch(Exception e){LOG.error("Config",e);}
        LOG.info("ChunkSpeedrun 1.0 for Minecraft 26.3");
    }
    public static boolean locked(){return run!=null&&!run.finished;}
    public static void signalInput(){if(locked())input=true;}
    public static void tick(MinecraftServer s,boolean paused){
        long now=System.nanoTime();
        if(server!=s){server=s;run=null;lastNano=now;previousPaused=true;input=false;ticks=0;}
        try {
            if(run==null){if(!s.getPlayerList().getPlayers().isEmpty())initialize(s,s.getPlayerList().getPlayers().getFirst());lastNano=System.nanoTime();return;}
            Run r=run;
            if(!r.finished){
                s.setDifficulty(Difficulty.HARD,true);s.setDifficultyLocked(true);
                s.getWorldData().setGameType(GameType.SURVIVAL);
                if(r.startedAt!=0 && !paused && !previousPaused)r.activeNanos+=Math.max(0,now-lastNano);
                if(input&&!paused&&r.startedAt==0){r.startedAt=System.currentTimeMillis();r.nickname=s.getPlayerList().getPlayers().getFirst().getGameProfile().name();save();}
                input=false;
                for(ServerPlayer p:s.getPlayerList().getPlayers()){
                    if(p.gameMode()!=GameType.SURVIVAL)p.setGameMode(GameType.SURVIVAL);
                    double x=Math.clamp(p.getX(),r.chunkX*16+.31,r.chunkX*16+15.69),z=Math.clamp(p.getZ(),r.chunkZ*16+.31,r.chunkZ*16+15.69);
                    if(p.level()!=s.overworld()||x!=p.getX()||z!=p.getZ()){
                        p.stopRiding();p.teleportTo(s.overworld(),x,p.level()!=s.overworld()?r.spawnY:p.getY(),z,Set.of(),p.getYRot(),p.getXRot(),false);
                    }
                }
                if(!paused&&blocksChanged){scan();blocksChanged=false;}
                if(!paused&&r.startedAt!=0&&r.remaining==0)finish();
                if(!paused&&++ticks%20==0){
                    updateBoards();
                }
                if(ticks%100==0)save();
                if(ticks%600==0)refresh();
                for(ServerPlayer p:s.getPlayerList().getPlayers())p.sendOverlayMessage(Component.literal(r.startedAt==0?"Чанк готов. Движение мыши / шаг — старт. /reset":format(r.activeNanos/1_000_000)+" ("+format(System.currentTimeMillis()-r.startedAt)+") • Осталось: "+r.remaining));
            } else if(!paused){if(++ticks%100==0)updateBoards();if(ticks%600==0)refresh();}
        }catch(Exception e){LOG.error("Challenge tick failed",e);}
        finally{lastNano=now;previousPaused=paused;}
    }
    private static void initialize(MinecraftServer s,ServerPlayer p)throws Exception{
        Path dir=s.getWorldPath(LevelResource.ROOT).resolve("chunkspeedrun");Files.createDirectories(dir);
        Path state=dir.resolve("run.json");
        Run r;
        if(Files.exists(state)){
            r=JSON.fromJson(Files.readString(state),Run.class);r.directory=dir;
            r.snapshotNbt=NbtIo.readCompressed(dir.resolve("initial.nbt"),NbtAccounter.unlimitedHeap());
            r.snapshot=r.snapshotNbt.getIntArray("blocks").orElseThrow();
            r.boards=new ArrayList<>();
        }else{
            r=new Run();r.directory=dir;r.minY=s.overworld().getMinY();r.maxY=s.overworld().getMaxY()+1;
            int ox=p.blockPosition().getX()>>4,oz=p.blockPosition().getZ()>>4;
            BlockPos spawn=null;
            search:for(int radius=0;radius<=24;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                int cx=ox+dx,cz=oz+dz;
                boolean wood=false,leaves=false;
                BlockPos safe=null;
                for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
                    int h=s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
                    for(int y=Math.max(r.minY,h-24);y<Math.min(r.maxY,h+2);y++){
                        BlockState b=s.overworld().getBlockState(new BlockPos(x,y,z));
                        wood|=b.is(BlockTags.LOGS);leaves|=b.is(BlockTags.LEAVES);
                    }
                    int ground=s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    BlockPos q=new BlockPos(x,ground,z);
                    if(x>cx*16&&x<cx*16+15&&z>cz*16&&z<cz*16+15&&s.overworld().getBlockState(q).isAir()&&s.overworld().getBlockState(q.above()).isAir()&&s.overworld().getBlockState(q.below()).isSolidRender()&&s.overworld().getFluidState(q.below()).isEmpty())safe=q;
                }
                if(wood&&leaves&&safe!=null){r.chunkX=cx;r.chunkZ=cz;spawn=safe;break search;}
            }
            if(spawn==null)throw new IllegalStateException("Не найден чанк с деревом в радиусе 24 чанков. Создайте обычный мир.");
            r.spawnX=spawn.getX();r.spawnY=spawn.getY();r.spawnZ=spawn.getZ();r.initialDayTime=s.overworld().getOverworldClockTime();
            var weather=s.overworld().getWeatherData();r.initialClear=weather.getClearWeatherTime();r.initialRainTime=weather.getRainTime();r.initialThunderTime=weather.getThunderTime();r.initialRain=weather.isRaining();r.initialThunder=weather.isThundering();
            var border=s.overworld().getWorldBorder();r.originalBorderSize=border.getSize();r.originalBorderX=border.getCenterX();r.originalBorderZ=border.getCenterZ();
            r.snapshot=new int[(r.maxY-r.minY)*256];r.snapshotNbt=new CompoundTag();
            CompoundTag entities=new CompoundTag();
            for(int i=0;i<r.snapshot.length;i++){
                BlockPos q=pos(r,i);r.snapshot[i]=Block.BLOCK_STATE_REGISTRY.getId(s.overworld().getBlockState(q));
                BlockEntity be=s.overworld().getBlockEntity(q);if(be!=null)entities.put(Integer.toString(i),be.saveWithFullMetadata(s.overworld().registryAccess()));
            }
            CompoundTag initialEntities=new CompoundTag();int entityIndex=0;
            for(Entity e:entities())if(!(e instanceof ServerPlayer)&&r.inside(e.blockPosition())){var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,s.overworld().registryAccess());if(e.save(out))initialEntities.put(Integer.toString(entityIndex++),out.buildResult());}
            r.snapshotNbt.put("entities",initialEntities);
            r.snapshotNbt.putIntArray("blocks",r.snapshot);r.snapshotNbt.put("blockEntities",entities);
            NbtIo.writeCompressed(r.snapshotNbt,dir.resolve("initial.nbt"));
            p.getInventory().clearContent();p.setExperienceLevels(0);p.setExperiencePoints(0);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);
            p.teleportTo(s.overworld(),r.spawnX+.5,r.spawnY,r.spawnZ+.5,Set.of(),0,0,false);
        }
        run=r;
        if(!r.finished){border();s.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(r.spawnX,r.spawnY,r.spawnZ),0,0));p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(r.spawnX,r.spawnY,r.spawnZ),0,0),true),false);}
        scan();save();makeBoards();updateBoards();refresh();
        p.sendSystemMessage(Component.literal("Чанк на скорость • Hard • /reset — восстановить исходный чанк. Для подтверждения запишите видео всей попытки."));
        LOG.info("Challenge ready: {},{}; remaining={}",r.chunkX,r.chunkZ,r.remaining);
    }
    public static BlockPos pos(Run r,int i){return new BlockPos(r.chunkX*16+(i&15),r.minY+(i>>8),r.chunkZ*16+((i>>4)&15));}
    private static void border(){var b=server.overworld().getWorldBorder();b.setCenter(run.chunkX*16+8,run.chunkZ*16+8);b.setSize(16);b.setWarningBlocks(0);b.setDamagePerBlock(0);}
    public static void scan(){Run r=run;r.remaining=0;for(int i=0;i<r.snapshot.length;i++){var b=server.overworld().getBlockState(pos(r,i));if(!b.isAir()&&!b.is(Blocks.BEDROCK))r.remaining++;}}
    public static int reset(ServerPlayer p){
        if(run==null||p==null)return 0;
        try{
            Run r=run;restoring=true;
            p.closeContainer();p.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);p.inventoryMenu.getInputGridSlots().forEach(slot->slot.set(net.minecraft.world.item.ItemStack.EMPTY));p.inventoryMenu.getResultSlot().set(net.minecraft.world.item.ItemStack.EMPTY);
            for(Entity e:entities())if(!(e instanceof ServerPlayer)&&r.inside(e.blockPosition()))e.discard();
            var area=new net.minecraft.world.level.levelgen.structure.BoundingBox(r.chunkX*16,r.minY,r.chunkZ*16,r.chunkX*16+15,r.maxY-1,r.chunkZ*16+15);
            server.overworld().getBlockTicks().clearArea(area);server.overworld().getFluidTicks().clearArea(area);
            for(int i=0;i<r.snapshot.length;i++){BlockPos q=pos(r,i);server.overworld().removeBlockEntity(q);server.overworld().setBlock(q,Blocks.AIR.defaultBlockState(),18);}
            for(int i=0;i<r.snapshot.length;i++)server.overworld().setBlock(pos(r,i),Block.BLOCK_STATE_REGISTRY.byId(r.snapshot[i]),18);
            CompoundTag bes=r.snapshotNbt.getCompound("blockEntities").orElse(new CompoundTag());
            for(String key:bes.keySet()){int i=Integer.parseInt(key);BlockEntity be=BlockEntity.loadStatic(pos(r,i),Block.BLOCK_STATE_REGISTRY.byId(r.snapshot[i]),bes.getCompound(key).orElseThrow(),server.overworld().registryAccess());if(be!=null)server.overworld().setBlockEntity(be);}
            CompoundTag originalEntities=r.snapshotNbt.getCompound("entities").orElse(new CompoundTag());
            for(String key:originalEntities.keySet()){Entity e=EntityType.loadEntityRecursive(originalEntities.getCompound(key).orElseThrow(),server.overworld(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);if(e!=null)server.overworld().addFreshEntityWithPassengers(e);}
            r.startedAt=0;r.activeNanos=0;r.finishedAt=0;r.finished=false;r.attemptId=UUID.randomUUID().toString();r.nickname="";
            server.overworld().clockManager().setTotalTicks(server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD),r.initialDayTime);
            var weather=server.overworld().getWeatherData();weather.setClearWeatherTime(r.initialClear);weather.setRainTime(r.initialRainTime);weather.setThunderTime(r.initialThunderTime);weather.setRaining(r.initialRain);weather.setThundering(r.initialThunder);
            p.getInventory().clearContent();p.getEnderChestInventory().clearContent();p.removeAllEffects();p.clearFire();p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(5);p.setExperienceLevels(0);p.setExperiencePoints(0);p.totalExperience=0;
            p.setGameMode(GameType.SURVIVAL);p.stopRiding();p.setDeltaMovement(Vec3.ZERO);p.resetFallDistance();
            p.teleportTo(server.overworld(),r.spawnX+.5,r.spawnY,r.spawnZ+.5,Set.of(),0,0,false);
            p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(r.spawnX,r.spawnY,r.spawnZ),0,0),true),false);
            border();input=false;lastNano=System.nanoTime();scan();makeBoards();updateBoards();save();
            p.sendSystemMessage(Component.literal("Исходный чанк восстановлен. Следующее движение начнёт новую попытку."));return 1;
        }catch(Exception e){LOG.error("Reset failed",e);p.sendSystemMessage(Component.literal("Ошибка восстановления: "+e.getMessage()));return 0;}finally{restoring=false;}
    }
    public static void save(){if(run==null)return;try{Path dest=run.directory.resolve("run.json"),tmp=run.directory.resolve("run.json.tmp");Files.writeString(tmp,JSON.toJson(run));Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){LOG.error("Save failed",e);}}
    public static void stop(MinecraftServer s){if(server==s){save();run=null;server=null;lastNano=0;input=false;}}
    private static void finish()throws Exception{
        Run r=run;r.finished=true;r.finishedAt=System.currentTimeMillis();
        Result result=new Result(r.attemptId,r.nickname,r.activeNanos/1_000_000,r.finishedAt-r.startedAt,"unverified","26.3-hard-air-bedrock-v1");
        Path records=GAME_DIR.resolve("chunkspeedrun-results");Files.createDirectories(records);Files.writeString(records.resolve(r.attemptId+".json"),JSON.toJson(result));
        save();var b=server.overworld().getWorldBorder();b.setCenter(r.originalBorderX,r.originalBorderZ);b.setSize(r.originalBorderSize);server.setDifficultyLocked(false);
        for(ServerPlayer p:server.getPlayerList().getPlayers())p.sendSystemMessage(Component.literal("ФИНИШ! "+format(result.activeMillis())+" ("+format(result.totalMillis())+"). ID: "+r.attemptId+". Результат не подтверждён. Сохраните видео и мир для модерации. Ограничения чанка сняты."));
        refresh();
    }
    public static String format(long ms){return String.format(Locale.ROOT,"%d:%02d:%02d.%03d",ms/3600000,ms/60000%60,ms/1000%60,ms%1000);}
    private static void makeBoards(){
        for(Entity e:entities())if(e.entityTags().contains("chunkspeedrun_board"))e.discard();
        run.boards=new ArrayList<>();
        for(int i=0;i<2;i++){
            Display.TextDisplay d=new Display.TextDisplay(EntityTypes.TEXT_DISPLAY,server.overworld());d.addTag("chunkspeedrun_board");
            ((ru.chunkspeedrun.mixin.TextAccess)d).csr$text(Component.literal("Загрузка таблицы…"));
            ((ru.chunkspeedrun.mixin.DisplayAccess)d).csr$billboard(Display.BillboardConstraints.CENTER);
            ((ru.chunkspeedrun.mixin.DisplayAccess)d).csr$range(16);
            ((ru.chunkspeedrun.mixin.TextAccess)d).csr$flags((byte)(Display.TextDisplay.FLAG_SHADOW|Display.TextDisplay.FLAG_SEE_THROUGH|Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND));
            ((ru.chunkspeedrun.mixin.TextAccess)d).csr$width(400);
            server.overworld().addFreshEntity(d);run.boards.add(d);
        }
    }
    private static void updateBoards(){
        if(run.boards==null||run.boards.size()!=2)return;
        int highest=run.minY;
        for(int x=run.chunkX*16;x<run.chunkX*16+16;x++)for(int z=run.chunkZ*16;z<run.chunkZ*16+16;z++)for(int y=run.maxY-1;y>=highest;y--)if(!server.overworld().getBlockState(new BlockPos(x,y,z)).isAir()){highest=y;break;}
        for(int i=0;i<2;i++){
            Display.TextDisplay d=run.boards.get(i);d.setPos(run.chunkX*16+(i==0?3.5:12.5),highest+5,run.chunkZ*16+8);
            String[] rows=i==0?verified:unverified;
            String text=(i==0?"§aПОДТВЕРЖДЁННЫЕ":"§eНЕПОДТВЕРЖДЁННЫЕ")+"\n§f"+(rows.length==0?"Пока нет результатов":String.join("\n",rows))+"\n§7"+networkStatus;
            ((ru.chunkspeedrun.mixin.TextAccess)d).csr$text(Component.literal(text));
        }
    }
    private static List<Entity> entities(){List<Entity> copy=new ArrayList<>();for(Entity e:server.overworld().getAllEntities())if(e!=null)copy.add(e);return copy;}
    public static void refresh(){NETWORK.submit(()->{
        localResults();if(config.repository==null||!config.repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")){networkStatus="Не указан GitHub-репозиторий";return;}
        try{String data=githubFile("leaderboard.json");JsonObject obj=JsonParser.parseString(data).getAsJsonObject();verified=rows(obj.getAsJsonArray("verified"));unverified=rows(obj.getAsJsonArray("unverified"));Files.writeString(GAME_DIR.resolve("chunkspeedrun-leaderboard-cache.json"),data);networkStatus="GitHub • обновлено при входе / каждые 30 с";}catch(Exception e){networkStatus="GitHub недоступен • локальные данные";try{Path cache=GAME_DIR.resolve("chunkspeedrun-leaderboard-cache.json");if(Files.exists(cache)){var obj=JsonParser.parseString(Files.readString(cache)).getAsJsonObject();verified=rows(obj.getAsJsonArray("verified"));unverified=rows(obj.getAsJsonArray("unverified"));}}catch(Exception ignored){}LOG.warn("Leaderboard: {}",e.toString());}
        try{Path records=GAME_DIR.resolve("chunkspeedrun-results");if(Files.isDirectory(records))try(var files=Files.list(records)){String endpoint=null;for(Path p:files.filter(p->p.toString().endsWith(".json")).toList()){Path marker=p.resolveSibling(p.getFileName()+".sent");if(!Files.exists(marker)||!Files.readString(marker).startsWith(config.repository+"\n")){if(endpoint==null)endpoint=config.submissionEndpoint!=null&&!config.submissionEndpoint.isBlank()?config.submissionEndpoint:JsonParser.parseString(githubFile("submission-endpoint.json")).getAsJsonObject().get("url").getAsString();submit(JSON.fromJson(Files.readString(p),Result.class),endpoint);}}}}catch(Exception e){LOG.warn("Pending GitHub results: {}",e.toString());networkStatus="Приёмник недоступен • результат ждёт отправки";}
    });}
    private static String[] rows(JsonArray arr){List<String> out=new ArrayList<>();if(arr!=null)for(JsonElement e:arr){JsonObject r=e.getAsJsonObject();out.add((out.size()+1)+". "+r.get("nickname").getAsString()+" "+format(r.get("activeMillis").getAsLong())+" ("+format(r.get("totalMillis").getAsLong())+")");if(out.size()==10)break;}return out.toArray(String[]::new);}
    private static void localResults(){try{Path dir=GAME_DIR.resolve("chunkspeedrun-results");if(!Files.isDirectory(dir))return;List<Result> rs=new ArrayList<>();try(var fs=Files.list(dir)){for(Path p:fs.filter(p->p.toString().endsWith(".json")).toList())rs.add(JSON.fromJson(Files.readString(p),Result.class));}rs.sort(Comparator.comparingLong(Result::activeMillis));unverified=rs.stream().limit(10).map(r->r.nickname()+" "+format(r.activeMillis())+" ("+format(r.totalMillis())+")").toArray(String[]::new);}catch(Exception e){LOG.warn("Local records",e);}}
    private static String githubFile(String name)throws Exception{
        String url="https://raw.githubusercontent.com/"+config.repository+"/main/"+name+"?t="+System.currentTimeMillis();
        var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent","ChunkSpeedrun-26.3").timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IOException("GitHub HTTP "+response.statusCode());return response.body();
    }
    private static void submit(Result result,String endpoint)throws Exception{
        if(result.activeMillis()<=0||result.totalMillis()<result.activeMillis()||!result.nickname().matches("[A-Za-z0-9_]{3,16}"))throw new IOException("Invalid local result");
        URI uri=URI.create(endpoint.replaceAll("/+$","")+"/results");if(!"https".equalsIgnoreCase(uri.getScheme())&&!("http".equalsIgnoreCase(uri.getScheme())&&Set.of("127.0.0.1","localhost").contains(uri.getHost())))throw new IOException("HTTPS receiver required");
        var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(result))).build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200&&response.statusCode()!=201)throw new IOException("Receiver HTTP "+response.statusCode());
        Files.writeString(GAME_DIR.resolve("chunkspeedrun-results").resolve(result.attemptId()+".json.sent"),config.repository+"\n"+response.body());
    }
}
