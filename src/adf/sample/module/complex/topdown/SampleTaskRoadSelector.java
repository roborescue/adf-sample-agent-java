package adf.sample.module.complex.topdown;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.topdown.CommandPolice;
import adf.agent.communication.standard.bundle.topdown.CommandScout;
import adf.agent.communication.standard.bundle.topdown.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.RoadSelector;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

import static rescuecore2.standard.entities.StandardEntityURN.POLICE_OFFICE;

public class SampleTaskRoadSelector extends RoadSelector {
    private EntityID task;
    private EntityID senderID;

    public SampleTaskRoadSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.task = null;
        this.senderID = null;
    }

    @Override
    public RoadSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.task != null) {
            Area area = this.agentInfo.getPositionArea();
            if(this.task.getValue() == area.getID().getValue()) {
                if(area instanceof Road) {
                    Road road = (Road)area;
                    if(!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
                        messageManager.addMessage(new MessageReport(true, true, false, this.senderID));
                        this.task = null;
                    }
                } else {
                    messageManager.addMessage(new MessageReport(true, true, false, this.senderID));
                    this.task = null;
                }
            }
        }
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> commanders = this.worldInfo.getEntityIDsOfType(POLICE_OFFICE);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(commanders.contains(command.getSenderID())) {
                if(command.getToID().getValue() == agentID.getValue()) {
                    this.task = null;
                    this.senderID = null;
                }
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class)) {
            CommandPolice command = (CommandPolice) message;
            if(!commanders.contains(command.getSenderID())) {
                continue;
            }
            if(command.isBroadcast() || command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            if(command.getAction() == CommandPolice.ACTION_CLEAR) {
                this.task = command.getTargetID();
                this.senderID = command.getSenderID();
                return this;
            } else {
                this.task = null;
                this.senderID = null;
            }
        }
        return this;
    }

    @Override
    public RoadSelector calc() {
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.task;
    }

    @Override
    public RoadSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public RoadSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public RoadSelector preparate() {
        super.preparate();
        return this;
    }
}
