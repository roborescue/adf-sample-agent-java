package adf.sample.module.complex.topdown;


import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.topdown.CommandFire;
import adf.agent.communication.standard.bundle.topdown.CommandScout;
import adf.agent.communication.standard.bundle.topdown.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.BuildingSelector;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleTaskBuildingSelector extends BuildingSelector {
    private int refillCompleted;

    private EntityID task;
    private EntityID senderID;


    public SampleTaskBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        int maxWater = this.scenarioInfo.getFireTankMaximum();
        //use DevelopData
        this.refillCompleted = (maxWater / 10) * developData.getInteger("fire.threshold.refill", 10);
        this.task = null;
        this.senderID = null;
    }

    @Override
    public BuildingSelector calc() {
        return this;
    }

    @Override
    public BuildingSelector updateInfo(MessageManager messageManager) {
        this.updateTask(messageManager);
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void updateTask(MessageManager messageManager) {
        FireBrigade agent = (FireBrigade) this.agentInfo.me();
        if(this.task != null) {
            StandardEntity entity = this.worldInfo.getEntity(this.task);
            StandardEntityURN urn = entity.getStandardURN();
            if(urn == REFUGE || urn == HYDRANT) {
                if(agent.getWater() >= this.refillCompleted) {
                    messageManager.addMessage(new MessageReport(true, true, false, this.senderID));
                    this.task = null;
                    this.senderID = null;
                }
            } else if(entity instanceof Building) {
                Building building = (Building)entity;
                if(!building.isOnFire()) {
                    messageManager.addMessage(new MessageReport(true, true, false, this.senderID));
                    this.task = null;
                    this.senderID = null;
                }
            } else {
                this.task = null;
                this.senderID = null;
            }
        }

        Collection<EntityID> commanders = this.worldInfo.getEntityIDsOfType(FIRE_STATION);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(commanders.contains(command.getSenderID())) {
                if(command.getToID().getValue() == agent.getID().getValue()) {
                    this.task = null;
                    this.senderID = null;
                }
            }
        }

        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire)message;
            if(!commanders.contains(command.getSenderID())) {
                continue;
            }
            if(command.isBroadcast() || command.getToID().getValue() != agent.getID().getValue()) {
                continue;
            }
            if(command.getAction() == CommandFire.ACTION_EXTINGUISH) {
                this.task = command.getTargetID();
                this.senderID = command.getSenderID();
                return;
            } else if(command.getAction() == CommandFire.ACTION_REFILL) {
                this.task = command.getTargetID();
                this.senderID = command.getSenderID();
                return;
            } else {
                this.task = null;
                this.senderID = null;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public EntityID getTarget() {
        return this.task;
    }

    @Override
    public BuildingSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public BuildingSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public BuildingSelector preparate() {
        super.preparate();
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
