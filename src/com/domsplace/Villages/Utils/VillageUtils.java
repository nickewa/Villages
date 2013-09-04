package com.domsplace.Villages.Utils;

import com.domsplace.Villages.Bases.DataManagerBase;
import com.domsplace.Villages.DataManagers.UpkeepManager;
import com.domsplace.Villages.Events.VillageDeletedEvent;
import com.domsplace.Villages.Objects.Village;
import com.domsplace.Villages.Objects.ItemBank;
import com.domsplace.Villages.Bases.UtilsBase;
import com.domsplace.Villages.Hooks.TagAPIHook;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class VillageUtils extends UtilsBase {
    public static int borderRadius = 1;
    public static ArrayList<Village> Villages;
    public static Map<Player, Village> townInvites = new HashMap<Player, Village>();
    
    public static void LoadAllVillages() {
        Villages = new ArrayList<Village>();
        
        List<String> vilNames = LoadVillageNames();
        for(String vn : vilNames) {
            Village v = LoadVillage(vn);
            Villages.add(v);
        }
        VillageScoreboardUtils.SetupScoreboard();
    }
    
    public static void SaveAllVillages() {
        for(Village v : Villages) {
            SaveVillage(v);
        }
        VillageScoreboardUtils.SetupScoreboard();
    }
    
    public static ArrayList<Village> getVillages() {
        return Villages;
    }
    
    public static List<String> LoadVillageNames() {
        List<String> towns = new ArrayList<String>();
        if(Utils.useSQL) {
            String stmt = "SELECT `VillageName` FROM `Villages` ORDER BY `VillageName` DESC;";
            List<Map<String, String>> results = VillageSQLUtils.sqlFetch(stmt);
            if(results == null) {
                return new ArrayList<String>();
            }
            for(Map<String, String> s : results) {
                towns.add(s.get("VillageName"));
            }
            return towns;
        }
        
        File villages = new File(getPlugin().getDataFolder() + "/data");
        if(!villages.exists()) {
            villages.mkdir();
        }
        villages = new File(getPlugin().getDataFolder() + "/data/villages/");
        if(!villages.exists()) {
            villages.mkdir();
        }
        
        ArrayList<String> names = new ArrayList<String>(Arrays.asList(villages.list()));
        for(String s : names) {
            towns.add(s.replaceAll(".yml", ""));
        }
        return towns;
    }
    
    public static Village LoadVillage(String name) {
        if(Utils.useSQL) {
            return LoadVillageSQL(name);
        }
        
        return LoadVillageYML(name);
    }
    
    public static Village LoadVillageSQL(String name) {
        String stmt = "SELECT * FROM `Villages` WHERE `VillageName` LIKE '" + name + "' LIMIT 1;";
        List<Map<String, String>> results = VillageSQLUtils.sqlFetch(stmt);
        if(results == null) {
            return null;
        }

        if(results.size() == 0) {
            return null;
        }
        Map<String, String> vil = results.get(0);
        if(vil == null) {
            return null;
        }

        Village village = null;

        try {
            village = new Village(vil.get("VillageName"));
            village.setDescription(vil.get("VillageDescription"));
            village.setCreatedDate(VillageSQLUtils.getSQLDate(vil.get("VillageCreateDate")).getTime());
            int x = Integer.parseInt(vil.get("VillageChunkX"));
            int z = Integer.parseInt(vil.get("VillageChunkZ"));
            Chunk chunk = Bukkit.getWorld(vil.get("VillageWorld")).getChunkAt(x, z);
            village.setTownSpawn(chunk);
            ArrayList<OfflinePlayer> players = getResidentsFromVillageSQL(Integer.parseInt(vil.get("VillageID")));
            if(players != null) {
                village.setResidents(players);
            }

            int id = Integer.parseInt(vil.get("VillageMayorID"));
            village.setMayor(VillageSQLUtils.getSQLPlayerByID(id));
            village.setMoney(Double.parseDouble(vil.get("VillageBank")));
            VillageUtils.LoadVillageBankSQL(village);
            
            //Fixed Village size not being saved using SQL
            village.setTownSize(Integer.parseInt(vil.get("VillageSize")));
            
            //Load in Village Plots
            stmt = "SELECT * FROM `VillagePlots` WHERE `VillageID`='" + village.idSQL + "';";
            results = VillageSQLUtils.sqlFetch(stmt);
            if(results != null) {
                if(results.size() > 0) {
                    for(Map<String, String> cData : results) {
                        int chunkX = Integer.parseInt(cData.get("ChunkX"));
                        int chunkZ = Integer.parseInt(cData.get("ChunkZ"));
                        World chunkWorld = Bukkit.getWorld(cData.get("ChunkWorld"));
                        Chunk c = chunkWorld.getChunkAt(chunkX, chunkZ);

                        if(cData.containsKey("VillagePlayerID") && cData.get("VillagePlayerID") != null && !cData.get("VillagePlayerID").equalsIgnoreCase("null")) {
                            //Player is set for this chunk, let's set the owner
                            OfflinePlayer p = VillageSQLUtils.getSQLPlayerByID(Integer.parseInt(cData.get("VillagePlayerID")));
                            if(p != null) {
                                village.forceClaim(p, c);
                            }
                        }

                        if(cData.containsKey("ChunkCost") && cData.get("ChunkCost") != null && !cData.get("ChunkCost").equalsIgnoreCase("null")) {
                            double cost = Double.parseDouble(cData.get("ChunkCost"));
                            village.setChunkPrice(c, cost);
                        }
                    }
                }
            }
            
        } catch(Exception ex) {
            Utils.Error("Failed to load village!", ex);
            return null;
        }
        
        return village;
    }
    
    public static ArrayList<OfflinePlayer> getResidentsFromVillageSQL(int villageID) {
        String stmt = ""
                + "SELECT VillagesPlayers.VillagePlayerName FROM "
                + "`VillagesPlayers` INNER JOIN `VillagesResidents` ON VillagesResidents.VillagePlayerID = VillagesPlayers.VillagePlayerID "
                + "WHERE VillagesResidents.VillageID='" + villageID + "';";
        List<Map<String, String>> results = VillageSQLUtils.sqlFetch(stmt);
        
        if(results == null) {
            return new ArrayList<OfflinePlayer>();
        }
        
        ArrayList<OfflinePlayer> players = new ArrayList<OfflinePlayer>();
        for(Map<String, String> iteration : results) {
            if(!iteration.containsKey("VillagePlayerName")) {
                continue;
            }
            
            OfflinePlayer player = Bukkit.getOfflinePlayer(iteration.get("VillagePlayerName"));
            players.add(player);
        }
        
        return players;
    }
    
    public static Village LoadVillageYML(String name) {
        Village village = null;
        
        try {
            File villageFile = new File(getPlugin().getDataFolder() + "/data/villages/" + name + ".yml");
            
            YamlConfiguration vil = YamlConfiguration.loadConfiguration(villageFile);
            
            village = new Village(vil.getString("name"));
            village.setDescription(vil.getString("description"));
            village.setMayor(Bukkit.getOfflinePlayer(vil.getString("mayor")));
            
            ArrayList<OfflinePlayer> residents = new ArrayList<OfflinePlayer>();
            for(String s : vil.getStringList("residents")) {
                residents.add(Bukkit.getOfflinePlayer(s));
            }
            village.setResidents(residents);
            village.setCreatedDate(vil.getLong("createDate"));
            village.setMoney(vil.getDouble("money"));
            village.setTownSize(vil.getInt("size"));
            
            //Load In Plots
            if(vil.contains("plots")) {
                //Iterate over the plots
                for(String s : ((MemorySection) vil.get("plots")).getKeys(false)) {
                    Chunk c = Bukkit.getWorld(vil.getString("plots." + s + ".world")).getChunkAt(Integer.parseInt(vil.getString("plots." + s + ".x")), Integer.parseInt(vil.getString("plots." + s + ".z")));
                    if(c == null) {
                        continue;
                    }
                    
                    if(vil.contains("plots." + s + ".player")) {
                        OfflinePlayer p = Bukkit.getOfflinePlayer(vil.getString("plots." + s + ".player"));
                        village.forceClaim(p, c);
                    }
                    
                    if(vil.contains("plots." + s + ".price")) {
                        double d = vil.getDouble("plots." + s + ".price");
                        village.setChunkPrice(c, d);
                    }
                }
            }
            
            Chunk chunk = Bukkit.getWorld(vil.getString("townsquare.world")).getChunkAt(vil.getInt("townsquare.x"), vil.getInt("townsquare.z"));
            village.setTownSpawn(chunk);
            VillageUtils.LoadVillageBankYML(village);
        } catch (Exception ex) {
            Utils.Error("Failed to load village!", ex);
            return null;
        }
        
        return village;
    }
    
    public static Village SaveVillage(Village village) {
        if(Utils.useSQL) {
            SaveVillageSQL(village);
        } else {
            SaveVillageYML(village);
        }
        
        SaveVillageBank(village);
        
        for(OfflinePlayer p : village.getResidents()) {
            if(!p.isOnline()) {
                continue;
            }
            
            TagAPIHook.instance.refreshTags();
        }
        
        VillageScoreboardUtils.SetupScoreboard();
        return village;
    }
    
    public static void SaveVillageYML(Village village) {
        try {
            File dataFolder = new File(getPlugin().getDataFolder() + "/data/");
            if(!dataFolder.exists()) {
                dataFolder.mkdir();
            }
            
            dataFolder = new File(getPlugin().getDataFolder() + "/data/villages/");
            if(!dataFolder.exists()) {
                dataFolder.mkdir();
            }
            
            File villageFile = new File(getPlugin().getDataFolder() + "/data/villages/" + village.getName() + ".yml");
            if(!villageFile.exists()) {
                villageFile.createNewFile();
            }
            
            YamlConfiguration configuration = village.getTownAsYML();
            configuration.save(villageFile);
        } catch (Exception ex) {
            Utils.Error("Failed to save village " + village.getName(), ex);
        }
    }
    
    public static void SaveVillageSQL(Village village) {
        int result = -1;
        //Try to get Village//
        result = VillageSQLUtils.getVillageIDByName(village.getName());

        if(result == -1) {
            String stmt = village.getCreateQuery();
            result = VillageSQLUtils.sqlQueryID(stmt);
            village.idSQL = result;
        } else {
            village.idSQL = result;
            String stmt = village.getUpdateQuery();
            if(stmt != null) {
                VillageSQLUtils.sqlQuery(stmt);
            }
        }

        ArrayList<OfflinePlayer> residents = village.getResidents();

        if(residents.size() < 1) {
            return;
        }

        String rstmt = "INSERT INTO `VillagesResidents` ("
                + "`VillagePlayerID`, "
                + "`VillageID`"
                + ") VALUES";

        for(OfflinePlayer p : residents) {
            int playerID = VillageSQLUtils.recordSQLPlayer(p);
            String stmt = "DELETE FROM `VillagesResidents` WHERE `VillagePlayerID` = '" + playerID + "';";
            VillageSQLUtils.sqlQuery(stmt);
            rstmt += "('" + playerID + "', '" + village.getSQLID() + "'), ";
        }
        rstmt = rstmt.substring(0, rstmt.length() - 2);
        rstmt += ";";
        VillageSQLUtils.sqlQuery(rstmt);
        
        //Save Plots
        rstmt = "DELETE FROM `VillagePlots` WHERE `VillageID`='" + village.idSQL + "';";
        VillageSQLUtils.sqlQuery(rstmt);
        
        //Add chunks, we'll run over them later to make the settings final
        String chunkstmt = "INSERT INTO `VillagePlots` ("
                + "`ChunkX`,"
                + "`ChunkZ`,"
                + "`ChunkWorld`,"
                + "`VillageID`"
                + ") VALUES ";
        
        String oldchunkstmt = chunkstmt;

        for(Chunk c :  village.getPlayerChunks().keySet()) {
            if(village.getTownSpawn().equals(c)) {
                continue;
            }

            chunkstmt += "('" + c.getX() + "', '" + c.getZ() + "', '" + c.getWorld().getName() + "', '" + village.idSQL + "'), ";
        }

        for(Chunk c :  village.getChunkPrices().keySet()) {
            if(village.getTownSpawn().equals(c)) {
                continue;
            }
            
            if(village.getPlayerChunks().containsKey(c)) {
                continue;
            }

            chunkstmt += "('" + c.getX() + "', '" + c.getZ() + "', '" + c.getWorld().getName() + "', '" + village.idSQL + "'), ";
        }
        if(!chunkstmt.equals(oldchunkstmt)) {
            chunkstmt = chunkstmt.substring(0, chunkstmt.length() - 2);
            chunkstmt += ";";
            
            VillageSQLUtils.sqlQuery(chunkstmt);

            //Now I've added the chunks, I can store the data that's been generated.

            for(Chunk c :  village.getPlayerChunks().keySet()) {
                if(village.getTownSpawn().equals(c)) {
                    continue;
                }
                
                VillageSQLUtils.sqlQuery(
                    "UPDATE `VillagePlots` SET `VillagePlayerID`='" + VillageSQLUtils.getSQLPlayerID(village.getPlayerFromChunk(c)) + "'"
                        + " WHERE `ChunkX`='" + c.getX() + "' "
                        + "AND `ChunkZ`='" + c.getZ() + "' "
                        + "AND `ChunkWorld`='" + c.getWorld().getName() + "'"
                        + "AND `VillageID`='" + village.idSQL + "';"
                );
            }

            for(Chunk c :  village.getChunkPrices().keySet()) {
                if(village.getTownSpawn().equals(c)) {
                    continue;
                }
                
                VillageSQLUtils.sqlQuery(
                    "UPDATE `VillagePlots` SET `ChunkCost`='" + village.getChunkPrice(c) + "'"
                        + " WHERE `ChunkX`='" + c.getX() + "' "
                        + "AND `ChunkZ`='" + c.getZ() + "' "
                        + "AND `ChunkWorld`='" + c.getWorld().getName() + "'"
                        + "AND `VillageID`='" + village.idSQL + "';"
                );
            }
        }
    }
    
    public static boolean isChunkInATownsArea(Chunk chunk) {
        for(Village v : Villages) {
            if(!v.isChunkInTownArea(chunk)) {
                continue;
            }
            return true;
        }
        
        return false;
    }
    
    public static boolean isChunkInATownsArea(Chunk chunk, Village ignored) {
        for(Village v : Villages) {
            if(v == ignored) {
                continue;
            }
            
            if(!v.isChunkInTownArea(chunk)) {
                continue;
            }
            return true;
        }
        
        return false;
    }

    public static Village getVillageFromChunk(Chunk chunk) {
        for(Village v : Villages) {
            if(v == null) {
                continue;
            }
            
            if(!v.isChunkInTownArea(chunk)) {
                continue;
            }
            return v;
        }
        return null;
    }
    
    public static List<World> getVillageWorlds() {
        List<World> worlds = new ArrayList<World>();
        for(String w : getConfig().getStringList("Worlds")) {
            World world = Bukkit.getWorld(w);
            if(world == null) {
                continue;
            }
            worlds.add(world);
        }
        
        return worlds;
    }
    
    public static boolean isVillageWorld(World world) {
        if(!getVillageWorlds().contains(world)) {
            return false;
        }
        return true;
    }
    
    public static Village getPlayerVillage(Player player) {
        return getPlayerVillage(Bukkit.getOfflinePlayer(player.getName()));
    }
    
    public static Village getPlayerVillage(OfflinePlayer player) {
        for(Village v : Villages) {
            if(v.isResident(player) || v.isMayor(player)) {
                return v;
            }
        }
        return null;
    }

    public static Village getVillage(String string) {
        for(Village v : Villages) {
            if(!v.getName().toLowerCase().startsWith(string.toLowerCase())) {
                continue;
            }
            return v;
        }
        return null;
    }

    public static Village getVillageExact(String string) {
        for(Village v : Villages) {
            if(!v.getName().toLowerCase().equals(string.toLowerCase())) {
                continue;
            }
            return v;
        }
        return null;
    }

    public static void DeleteVillage(Village village) {
        VillageDeletedEvent event = new VillageDeletedEvent(village);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if(event.isCancelled()) {
            return;
        }
        
        if(Utils.useSQL) {
            int id = VillageSQLUtils.getVillageIDByName(village.getName());
            String stmt = "DELETE FROM `Villages` WHERE VillageID='" + id + "';";
            VillageSQLUtils.sqlQuery(stmt);
            
            stmt = "DELETE FROM `VillagesResidents` WHERE VillageID='" + id + "';";
            VillageSQLUtils.sqlQuery(stmt);
            
            stmt = "DELETE FROM `VillageBankItems` WHERE VillageID='" + id + "';";
            VillageSQLUtils.sqlQuery(stmt);
            
            stmt = "DELETE FROM `VillagePlots` WHERE `VillageID`='" + id+ "';";
            VillageSQLUtils.sqlQuery(stmt);
        } else {
            File vill = new File(getPlugin().getDataFolder() + "/data/villages/" + village.getName() + ".yml");
            vill.delete();
        }
        Villages.remove(village);
        Utils.broadcast(gK("villageclosed", village));
        DataManagerBase.UPKEEP_MANAGER.DeleteUpkeep(village);
        village = null;
        VillageScoreboardUtils.SetupScoreboard();
    }
    
    public static boolean doVillagesOverlap(Village v) {
        for(Chunk c : v.getTownArea()) {
            if(!isChunkInATownsArea(c, v)) {
                continue;
            }
            return true;
        }
        return false;
    }
    
    public static double CreateVillageCost() {
        return getConfig().getDouble("cost.createvillage");
    }
    
    public static void SaveVillageBank(Village village) {
        //Todo finish//
        ItemBank vBank = village.getItemBank();
        
        if(!Utils.useSQL) {
            return;
        }
        //Dont need to save bank using YML, since the normal village save does that.
        
        //Clear Items
        if(village.idSQL == -1) {
            village.idSQL = VillageSQLUtils.getVillageIDByName(village.getName());
        }
        
        String stmt = "DELETE FROM `VillageBankItems` WHERE VillageID='" + village.idSQL + "';";
        VillageSQLUtils.sqlQuery(stmt);
        
        //Get insert statement
        
        if(vBank.getItems().size() < 1) {
            return;
        }
        
        stmt = "INSERT INTO `VillageBankItems` ("
                + "`ItemID`,"
                + "`ItemData`,"
                + "`ItemAmount`,"
                + "`VillageID`"
                + ") VALUES (";
        
        //Now add records
        for(ItemStack is : vBank.getItems()) {
            stmt += "'" + is.getTypeId() + "', '" + is.getData().getData() + "', '" + is.getAmount() + "', '" + village.idSQL + "'), (";
        }
        
        stmt = stmt.substring(0, stmt.length() - 3);
        stmt += ";";
        
        VillageSQLUtils.sqlQuery(stmt);
    }
    
    public static void LoadVillageBank(Village village) {
        if(Utils.useSQL) {
            LoadVillageBankSQL(village);
            return;
        }
        
        LoadVillageBankYML(village);
    }
    
    public static void LoadVillageBankSQL(Village village) {
        village.getItemBank().setItems(VillageSQLUtils.getVillageItems(village));
    }
    
    public static void LoadVillageBankYML(Village village) {
        File villageFile = new File(getPlugin().getDataFolder() + "/data/villages/" + village.getName() + ".yml");
        YamlConfiguration vil = YamlConfiguration.loadConfiguration(villageFile);
        
        List<ItemStack> items = Utils.GetItemFromString(vil.getStringList("bank"));
        village.getItemBank().setItems(items);
    }

    public static List<Inventory> getVillageInventories() {
        List<Inventory> invs = new ArrayList<Inventory>();
        
        for(Village v : VillageUtils.getVillages()) {
            invs.add(v.getItemBank().getInventory());
        }
        
        return invs;
    }

    static List<OfflinePlayer> getAllVillagesPlayers() {
        List<OfflinePlayer> players = new ArrayList<OfflinePlayer>();
        
        for(Village v : Villages) {
            for(OfflinePlayer p : v.getResidents()) {
                players.add(p);
            }
        }
        
        return players;
    }
    
    public static boolean isCoordBetweenCoords(Chunk c, ProtectedRegion r) {
        Block b1 = c.getBlock(0, 0, 0);
        Block b2 = c.getBlock(15, 0, 15);
        
        boolean s1 = isCoordBetweenCoords(b1.getX(), b1.getZ(), r.getMinimumPoint(), r.getMaximumPoint());
        if(s1) return true;
        boolean s2 = isCoordBetweenCoords(b2.getX(), b2.getZ(), r.getMinimumPoint(), r.getMaximumPoint());
        if(s2) return true;
        boolean s3 = isCoordBetweenCoords(r.getMinimumPoint(), b1, b2);
        if(s3) return true;
        boolean s4 = isCoordBetweenCoords(r.getMaximumPoint(), b1, b2);
        if(s4) return true;
        
        return false;
    }
    
    public static boolean isCoordBetweenCoords(int checkX, int checkZ, BlockVector min, BlockVector max) {
        return isCoordBetweenCoords(checkX, checkZ, min.getBlockX(), min.getBlockZ(), max.getBlockX(), max.getBlockZ());
    }
    
    public static boolean isCoordBetweenCoords(BlockVector bv, int outerX, int outerZ, int maxX, int maxZ) {
        return isCoordBetweenCoords(bv.getBlockX(), bv.getBlockZ(), outerX, outerZ, maxX, maxZ);
    }
    
    public static boolean isCoordBetweenCoords(BlockVector bv, Block b1, Block b2) {
        return isCoordBetweenCoords(bv.getBlockX(), bv.getBlockZ(), b1.getX(), b1.getZ(), b2.getX(), b2.getZ());
    }
    
    public static boolean isCoordBetweenCoords(int checkX, int checkZ, int outerX, int outerZ, int maxX, int maxZ) {
        if(checkX >= Math.min(outerX, maxX) && checkX <= Math.max(outerX, maxX)) {
            if(checkZ >= Math.min(outerZ, maxZ) && checkZ <= Math.max(outerZ, maxZ)) { return true; }
        }
        return false;
    }
}
