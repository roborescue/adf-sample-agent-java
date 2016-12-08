package adf.sample.centralized;

import adf.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.centralized.CommandPicker;
import adf.component.communication.CommunicationMessage;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class CommandPickerAmbulance extends CommandPicker {
    private int scoutDistance;

    private Collection<CommunicationMessage> messages;
    private Map<EntityID, EntityID> allocationData;

    public CommandPickerAmbulance(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.messages = new ArrayList<>();
        this.allocationData = null;
        this.scoutDistance = developData.getInteger("CommandPickerAmbulance.scoutDistance", 40000);
    }

    @Override
    public CommandPicker setAllocatorResult(Map<EntityID, EntityID> allocationData) {
        this.allocationData = allocationData;
        return this;
    }

    @Override
    public CommandPicker calc() {
        this.messages.clear();
        if(this.allocationData == null) {
            return this;
        }
        for(EntityID agentID : this.allocationData.keySet()) {
            StandardEntity agent = this.worldInfo.getEntity(agentID);
            if(agent != null && agent.getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
                StandardEntity target = this.worldInfo.getEntity(this.allocationData.get(agentID));
                if(target != null) {
                    if(target instanceof Human) {
                        CommandAmbulance command = new CommandAmbulance(
                                true,
                                agentID,
                                target.getID(),
                                CommandAmbulance.ACTION_AUTONOMY
                        );
                        this.messages.add(command);
                    } else if(target instanceof Area) {
                        CommandScout command = new CommandScout(
                                true,
                                agentID,
                                target.getID(),
                                this.scoutDistance
                        );
                        this.messages.add(command);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public Collection<CommunicationMessage> getResult() {
        return this.messages;
    }


}
