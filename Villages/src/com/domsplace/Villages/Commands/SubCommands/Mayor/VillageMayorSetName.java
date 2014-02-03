/*
 * Copyright 2013 Dominic Masters and Jordan Atkins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.domsplace.Villages.Commands.SubCommands.Mayor;

import com.domsplace.Villages.Bases.BukkitCommand;
import com.domsplace.Villages.Bases.DataManager;
import com.domsplace.Villages.Bases.SubCommand;
import com.domsplace.Villages.Commands.SubCommands.VillageCreate;
import com.domsplace.Villages.Objects.Resident;
import com.domsplace.Villages.Objects.Village;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class VillageMayorSetName extends SubCommand {
    public VillageMayorSetName() {
        super("village", "mayor", "set", "name");
        this.setPermission("mayor.setname");
    }
    
    @Override
    public boolean cmd(BukkitCommand bkcmd, CommandSender sender, Command cmd, String label, String[] args) {
        if(!isPlayer(sender)) {sk(sender, "playeronly");return true;}
        
        Resident r = Resident.getResident(getPlayer(sender));
        Village v = Village.getPlayersVillage(r);
        if(v == null) {sk(sender, "notinvillage");return true;}
        if(!v.isMayor(r)) {sk(sender, "mayorkickonly"); return true;}
        
        if(args.length < 1) {
            sk(sender, "needvillagename");
            return false;
        }
        
        String name = args[0];
        if(name.length() >= VillageCreate.VILLAGE_NAME_LENGTH || name.length() < 3) {
            sk(sender, "invalidvillagename");
            return true;
        }
        
        if(!name.matches(VillageCreate.VILLAGE_NAME_REGEX)) {
            sk(sender, "invalidvillagename");
            return true;
        }
        
        if(Village.getVillage(name) != null) {
            sk(sender, "villagenameused");
            return true;
        }
        
        DataManager.VILLAGE_MANAGER.changeVillageName(v, name);
        v.setName(name);
        sk(sender, "villagenamechaned", v);
        DataManager.saveAll();
        return true;
    }
}
